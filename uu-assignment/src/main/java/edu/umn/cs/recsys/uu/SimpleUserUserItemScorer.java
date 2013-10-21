package edu.umn.cs.recsys.uu;

import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.grouplens.lenskit.basic.AbstractItemScorer;
import org.grouplens.lenskit.data.dao.ItemEventDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.History;
import org.grouplens.lenskit.data.history.RatingVectorUserHistorySummarizer;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User-user item scorer.
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleUserUserItemScorer extends AbstractItemScorer {
    private static final Logger logger = LoggerFactory.getLogger(SimpleUserUserItemScorer.class);
    private static final int SIZE = 30;

    private final UserEventDAO userDao;
    private final ItemEventDAO itemDao;

    private final CosineVectorSimilarity cosineVectorSimilarity = new CosineVectorSimilarity();

    @Inject
    public SimpleUserUserItemScorer(UserEventDAO udao, ItemEventDAO idao) {
        userDao = udao;
        itemDao = idao;
    }

    @Override
    public void score(long user, @Nonnull MutableSparseVector scores) {
	logger.debug("Score for User :{}", user);
        SparseVector userVector = getUserRatingVector(user);

        MutableSparseVector meanCenteredUserVector = userVector.mutableCopy();
        meanCenteredUserVector.add(userVector.mean() * -1);

        for (VectorEntry e: scores.fast(VectorEntry.State.EITHER)) {
            long item = e.getKey();
            logger.debug("Score for Item :{}", item);
            Iterator<UserScore> it = topN(user, item, meanCenteredUserVector);
            double numerator = 0.0;
            double denominator = 0.0;
            while (it.hasNext()) {
        	UserScore us = it.next();
        	SparseVector v = getUserRatingVector(us.getUserId());
        	numerator += us.getScore() * (v.get(item) - v.mean());
        	denominator += Math.abs(us.getScore());
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

    private Iterator<UserScore> topN(long user, long itemId, MutableSparseVector meanCenteredUserVector) {
	 /*
         * Create a min heap
         */
        PriorityQueue<UserScore> topN = new PriorityQueue<UserScore>(11, new Comparator<UserScore>() {
	    	@Override
	    	public int compare(UserScore o1, UserScore o2) {
	    	    return o1.getScore().compareTo(o2.getScore());
	    	}
	    });
        LongSet users = itemDao.getUsersForItem(itemId);
        for (long aUser : users) {
            MutableSparseVector aUserVector = getUserRatingVector(aUser).mutableCopy();
    	    aUserVector.add(aUserVector.mean() * -1);
    	    Double similarity = cosineVectorSimilarity.similarity(meanCenteredUserVector, aUserVector);

    	    /*
    	     * If the list of user contains the user against whom we are calculating the
    	     * similarity, then skip AND if the min heap has reached maximum size and the next
    	     * user has a score which is lesser than the smallest one then skip.
    	     */
    	    if(aUser == user || (topN.size() == SIZE && topN.peek().getScore() > similarity)) {
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
        return topN.iterator();
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
