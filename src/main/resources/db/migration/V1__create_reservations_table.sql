CREATE TABLE IF NOT EXISTS reservations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reservation_code VARCHAR(50) NOT NULL UNIQUE,
    owner_name VARCHAR(100) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    cat_name VARCHAR(100) NOT NULL,
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL,
    room_type VARCHAR(100) NOT NULL,
    note_text TEXT,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP
);

IINSERT INTO reservations (reservation_code, owner_name, phone, email, cat_name, check_in_date, check_out_date, room_type, note_text) VALUES
 ('RES-2026112401', '佐藤 美咲', '090-1122-3344', 's408781748@gmail.com', 'モモ', '2026-11-23', '2026-11-26', 'スタンダードルーム', '人見知りですがおやつが好きです'),
 ('RES-2026101002', '佐藤 美咲', '090-1122-3344', 's408781748@gmail.com', 'モモ', '2026-10-10', '2026-10-12', 'スタンダードルーム', '前回の利用時はよく食べました'),
 ('RES-2026120303', '田中 翔太', '070-4455-6677', 's408781748@gmail.com', 'むぎ', '2026-12-03', '2026-12-05', 'サンシャイン・ラグジュアリールーム', '朝晩1回ずつ投薬あり');