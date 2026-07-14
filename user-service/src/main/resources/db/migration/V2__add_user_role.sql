ALTER TABLE app_user
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

ALTER TABLE app_user
    ADD CONSTRAINT chk_app_user_role CHECK (role IN ('USER', 'ADMIN'));
