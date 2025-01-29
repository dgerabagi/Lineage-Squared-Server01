CREATE TABLE IF NOT EXISTS `character_unlocks` (
    `char_id` INT(11) NOT NULL,
    `class_id` INT(2) NOT NULL,
    `level` TINYINT UNSIGNED NOT NULL DEFAULT 2,
    `exp` BIGINT UNSIGNED NOT NULL DEFAULT 20,
    PRIMARY KEY (`char_id`,`class_id`)
) ENGINE=MyISAM;