CREATE TABLE member
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    email      VARCHAR(100) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    nickname   VARCHAR(50)  NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_email UNIQUE (email)
);

CREATE TABLE coupon_event
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL,
    total_quantity  INT          NOT NULL,
    issued_quantity INT          NOT NULL DEFAULT 0,
    start_at        DATETIME     NOT NULL,
    end_at          DATETIME     NOT NULL,
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE issued_coupon
(
    id              BIGINT   NOT NULL AUTO_INCREMENT,
    member_id       BIGINT   NOT NULL,
    coupon_event_id BIGINT   NOT NULL,
    used            BOOLEAN  NOT NULL DEFAULT FALSE,
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_issued_coupon_member_event UNIQUE (member_id, coupon_event_id)
);
