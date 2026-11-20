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

INSERT INTO reservations (reservation_code, owner_name, phone, email, cat_name, check_in_date, check_out_date, room_type, note_text) VALUES
('RES-2026101001', '佐藤 美咲', '090-1122-3344', 'misaki.sato@example.com', 'モモ', '2026-10-10', '2026-10-13', 'スタンダードルーム', '初回利用・人見知りですがおやつが好きです'),
('RES-2026112002', '森本 蓮', '080-1010-2020', 'ren.morimoto@example.com', 'クロ', '2026-11-20', '2026-11-23', 'スタンダードルーム', '本日チェックイン予定'),
('RES-2026112803', '佐藤 美咲', '090-1122-3344', 'misaki.sato@example.com', 'モモ', '2026-11-28', '2026-12-01', 'スタンダードルーム', 'リピート利用・前回と同じお部屋希望'),
('RES-2026120304', '田中 美穂', '070-4455-6677', 'family.tanaka@example.com', 'しらす', '2026-12-03', '2026-12-06', 'サンシャイン・ラグジュアリールーム', '朝晩1回ずつ投薬あり'),
('RES-2026121005', '田中 翔太', '070-4455-6677', 'family.tanaka@example.com', 'むぎ', '2026-12-10', '2026-12-13', 'ファミリー2匹ルーム', 'おもちゃ持参します');