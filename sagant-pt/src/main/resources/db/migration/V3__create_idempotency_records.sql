CREATE TABLE idempotency_records (
         idempotency_key VARCHAR(255) PRIMARY KEY,
         notification_id BIGINT UNIQUE,
         created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

         CONSTRAINT fk_idempotency_notification
            FOREIGN KEY (notification_id)
            REFERENCES notifications (id_notification)
);