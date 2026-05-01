// 1. パッケージ名を Clover に統一
// 将包名统一修改为 Clover
package com.clover.cathotel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * クローバー猫ホテルのメインエントリーポイント
 * Clover猫咪酒店的项目启动入口
 */
@SpringBootApplication
public class CatHotelApplication {

    public static void main(String[] args) {
        // 2. クラス名を Orange から CatHotel に変更して実行
        // 将类名从 Orange 改为 CatHotel 并运行
        SpringApplication.run(CatHotelApplication.class, args);
    }

}