package edu.umn.cs.recsys.uu;

import static java.lang.Math.sqrt;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.grouplens.lenskit.basic.AbstractItemScorer;
import org.grouplens.lenskit.collections.LongUtils;
import org.grouplens.lenskit.data.dao.ItemEventDAO;
import org.grouplens.lenskit.data.dao.UserDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.History;
import org.grouplens.lenskit.data.history.RatingVectorUserHistorySummarizer;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.grouplens.lenskit.vectors.Vectors;
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity;
import org.grouplens.lenskit.vectors.similarity.PearsonCorrelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User-user item scorer.
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleUserUserItemScorer extends AbstractItemScorer {
    private static final Logger logger = LoggerFactory.getLogger(SimpleUserUserItemScorer.class);
    private static final int SIZE = 25;

    private final UserEventDAO userDao;
    private final ItemEventDAO itemDao;
    private final UserDAO allUserDao;
    
    private final PearsonCorrelation cosineVectorSimilarity = new PearsonCorrelation();

    @Inject
    public SimpleUserUserItemScorer(UserEventDAO udao, ItemEventDAO idao, UserDAO allUserDao) {
        userDao = udao;
        itemDao = idao;
        this.allUserDao = allUserDao;
    }

    @Override
    public void score(long user, @Nonnull MutableSparseVector scores) {
	logger.debug("Score for User :{}", user);
        SparseVector userVector = getUserRatingVector(user);

        MutableSparseVector meanCenteredUserVector = userVector.mutableCopy();
        //meanCenteredUserVector.add(userVector.mean() * -1);
        UserScore[] userScores = topN(user, meanCenteredUserVector);
        
        for(UserScore us : userScores) {
        	System.out.format("%s %s %s %s\n",user,us.getUserId(),us.getScore(),getUserRatingVector(us.getUserId()).toString());
        }
        
        for (VectorEntry e: scores.fast(VectorEntry.State.EITHER)) {
        	long item = e.getKey();
            logger.debug("Score for Item :{}", item);
            double numerator = 0.0;
            double denominator = 0.0;
            for(UserScore us : userScores) {
	        	SparseVector v = getUserRatingVector(us.getUserId());
	        	numerator += us.getScore() * (v.containsKey(item)?v.get(item):0.0);
	        	denominator += (us.getScore());
            }
            scores.set(item, userVector.mean() + (numerator/denominator));
        }
    }

    /**
     * Get a user's rating vector.
     * @param user The user ID.
     * @return The rating vector.
     */
    private SparseVector getUserRatingVector(long user) {
        UserHistory<Rating> history = userDao.getEventsForUser(user, Rating.class);
        if (history == null) {
            history = History.forUser(user);
        }
        return RatingVectorUserHistorySummarizer.makeRatingVector(history);
    }

    private UserScore[] topN(long user, MutableSparseVector meanCenteredUserVector) {
	 /*
         * Create a min heap
         */
        PriorityQueue<UserScore> topN = new PriorityQueue<UserScore>(11, new Comparator<UserScore>() {
	    	@Override
	    	public int compare(UserScore o1, UserScore o2) {
	    	    return o1.getScore().compareTo(o2.getScore());
	    	}
	    });
        LongSet users = allUserDao.getUserIds();
        for (long aUser : users) {
            MutableSparseVector aUserVector = getUserRatingVector(aUser).mutableCopy();
    	    //aUserVector.add(aUserVector.mean() * -1);
    	    Double similarity = cosineVectorSimilarity.similarity(meanCenteredUserVector, aUserVector);

    	    /*
    	     * If the list of user contains the user against whom we are calculating the
    	     * similarity, then skip AND if the min heap has reached maximum size and the next
    	     * user has a score which is lesser than the smallest one then skip.
    	     */
    	    if((topN.size() == SIZE && topN.peek().getScore() > similarity)) {
    	    	continue;
            }

	    	/*
		     * if the min heap has reached the 30 limit, then remove the smallest score
		     */
		    if (topN.size() == SIZE ) {
		    	topN.poll();
		    }
		    topN.offer(new UserScore(aUser, similarity));
        }
        return topN.toArray(new UserScore[1]);
    }
}

class UserScore {
    private Long userId;

    private Double score;

    UserScore(Long userId, Double score) {
	this.score = score;
	this.userId  = userId;
    }

    Long getUserId() {
        return userId;
    }

    Double getScore() {
        return score;
    }

    @Override
    public boolean equals(Object o) {
	return ((Long) o).equals(userId);
    }
}

class Correlation extends PearsonCorrelation {
	private static final long serialVersionUID = 8454471996355493876L;
	
	public Correlation() {
		super();
	}

	@Override
    public double similarity(SparseVector vec1, SparseVector vec2) {
        // First check for empty vectors - then we can assume at least one element
        if (vec1.isEmpty() || vec2.isEmpty()) {
            return 0;
        }

        /*
         * Basic similarity: walk in parallel across the two vectors, computing
         * the dot product and simultaneously computing the variance within each
         * vector of the items also contained in the other vector.  Pearson
         * correlation only considers items shared by both vectors; other items
         * are discarded for the purpose of similarity computation.
         */
        final double mu1 = vec1.mean();
        final double mu2 = vec2.mean();

        double var1 = 0;
        double var2 = 0;
        double dot = 0;
        int nCoratings = 0;
        LongSortedSet keys1 = vec1.keySet();
        LongSortedSet keys2 = vec2.keySet();
        
        for (long key: LongUtils.setUnion(keys1, keys2)) {
            final double v1 = vec1.containsKey(key)?vec1.get(key):0.0 - mu1;
            final double v2 = vec2.containsKey(key)?vec2.get(key):0.0 - mu2;
            var1 += v1 * v1;
            var2 += v2 * v2;
            dot += v1 * v2;
            nCoratings += 1;
        }

        if (nCoratings == 0) {
            return 0;
        } else {
            return dot / (sqrt(var1 * var2));
        }
    }
}
