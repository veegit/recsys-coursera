-- Dumping database structure for kaggle_fb
CREATE DATABASE IF NOT EXISTS `recsys` /*!40100 DEFAULT CHARACTER SET latin1 */;
USE `recsys`;


-- Dumping structure for table recsys.tags
CREATE TABLE IF NOT EXISTS `users` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int(10) NOT NULL,
  `name` char(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

-- Dumping structure for table recsys.train
CREATE TABLE IF NOT EXISTS `movies` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `movie_id` int(10) NOT NULL,
  `name` char(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COMMENT='train data';

-- Dumping structure for table recsys.train_tag_mapping
CREATE TABLE IF NOT EXISTS `ratings` (
  `id` int(10) NOT NULL AUTO_INCREMENT,
  `user_id` int(10) NOT NULL,
  `movie_id` int(10) NOT NULL,
  `rating` int(10) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id_movie_id` (`user_id`,`movie_id`),
  KEY `user_id` (`user_id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

