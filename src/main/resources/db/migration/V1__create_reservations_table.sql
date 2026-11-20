REATE TABLE IF NOT EXISTS reservations (
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
('RES-2026110101', '佐藤 美咲', '090-1122-3344', 's408781748@gmail.com', 'モモ', '2026-11-02', '2026-11-05', 'スタンダードルーム', '人見知りですがおやつが好きです'),
('RES-2026110102', '鈴木 健太', '080-2233-4455', 'cu408781748@gmail.com', 'レオ', '2026-11-08', '2026-11-11', 'サンシャイン・ラグジュアリールーム', '朝晩1回ずつ投薬あり'),
('RES-2026110103', '高橋 陽子', '090-3344-5566', 's408781748@gmail.com', 'ソラ・ウミ', '2026-11-15', '2026-11-19', 'ファミリー2匹ルーム', '2匹とも同じケージで大丈夫です'),
('RES-2026110104', '田中 翔太', '070-4455-6677', 'cu408781748@gmail.com', 'むぎ', '2026-11-16', '2026-11-20', 'スタンダードルーム', ''),
('RES-2026110105', '伊藤 さくら', '090-5566-7788', 's408781748@gmail.com', 'きなこ', '2026-11-17', '2026-11-21', 'スタンダードルーム', 'アレルギーのため魚系おやつNG'),
('RES-2026110106', '渡辺 裕子', '080-6677-8899', 'cu408781748@gmail.com', 'ココ', '2026-11-22', '2026-11-25', 'ファミリー2匹ルーム', '初めての利用、爪切り希望'),
('RES-2026110107', '山本 大輔', '090-7788-9900', 's408781748@gmail.com', 'ルナ', '2026-11-25', '2026-11-28', 'サンシャイン・ラグジュアリールーム', 'ブラッシング希望'),
('RES-2026110108', '中村 結衣', '080-8899-0011', 'cu408781748@gmail.com', 'まる', '2026-12-01', '2026-12-04', 'スタンダードルーム', ''),
('RES-2026110109', '小林 拓海', '090-9900-1122', 's408781748@gmail.com', 'チョコ・ミント', '2026-12-05', '2026-12-09', 'ファミリー2匹ルーム', '夜鳴きすることがあります'),
('RES-2026110110', '加藤 愛美', '070-1234-5678', 'cu408781748@gmail.com', 'ひなた', '2026-12-10', '2026-12-13', 'スタンダードルーム', 'フードは持参します');