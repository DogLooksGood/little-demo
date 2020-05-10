CREATE TABLE `demo`.`posts` (
  `id` INT(11) AUTO_INCREMENT,
  `poster` VARCHAR(256),
  `reply` INT(11),
  `content` TEXT,
  `created` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(`id`)
) charset=UTF8;
