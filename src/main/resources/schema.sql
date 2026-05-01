CREATE TABLE IF NOT EXISTS reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_code VARCHAR(50) NOT NULL,
    owner_name VARCHAR(100) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(100),
    cat_name VARCHAR(100) NOT NULL,
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL,
    room_type VARCHAR(100),
    note_text TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);