select r2.movie_id, count(*)
from movies m, ratings r, ratings r2
where m.movie_id = r.movie_id
AND r.user_id = r2.user_id
AND m.movie_id = 10020
group by r2.movie_id
order by r2.movie_id

select movie_id,count(*)
from ratings where user_id not in
( select distinct user_id from ratings where movie_id = 10020 )
group by movie_id
order by movie_id;

