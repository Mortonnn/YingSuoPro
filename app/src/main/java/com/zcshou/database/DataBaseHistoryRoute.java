package com.zcshou.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.elvishew.xlog.XLog;

public class DataBaseHistoryRoute extends SQLiteOpenHelper {
    public static final String TABLE_NAME = "HistoryRoute";
    public static final String DB_COLUMN_ID = "DB_COLUMN_ID";
    public static final String DB_COLUMN_NAME = "DB_COLUMN_NAME";
    public static final String DB_COLUMN_POINTS = "DB_COLUMN_POINTS";
    public static final String DB_COLUMN_DISTANCE = "DB_COLUMN_DISTANCE";
    public static final String DB_COLUMN_ALTITUDE = "DB_COLUMN_ALTITUDE";
    public static final String DB_COLUMN_TIMESTAMP = "DB_COLUMN_TIMESTAMP";

    private static final int DB_VERSION = 1;
    private static final String DB_NAME = "HistoryRoute.db";
    /* DB_COLUMN_POINTS 中保存的是路线的途经点，格式为 “经度,纬度;经度,纬度;...”，坐标系为 BD09 */
    private static final String CREATE_TABLE = "create table if not exists " + TABLE_NAME +
            " (DB_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, DB_COLUMN_NAME TEXT NOT NULL, " +
            "DB_COLUMN_POINTS TEXT NOT NULL, DB_COLUMN_DISTANCE TEXT NOT NULL, " +
            "DB_COLUMN_ALTITUDE TEXT NOT NULL, DB_COLUMN_TIMESTAMP BIGINT NOT NULL)";

    public DataBaseHistoryRoute(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        String sql = "DROP TABLE IF EXISTS " + TABLE_NAME;
        sqLiteDatabase.execSQL(sql);
        onCreate(sqLiteDatabase);
    }

    // 保存路线，同名路线会被覆盖
    public static void saveHistoryRoute(SQLiteDatabase sqLiteDatabase, ContentValues contentValues) {
        try {
            // 先删除原来的记录，再插入新记录
            String name = contentValues.getAsString(DB_COLUMN_NAME);
            sqLiteDatabase.delete(TABLE_NAME, DB_COLUMN_NAME + " = ?", new String[] {name});
            sqLiteDatabase.insert(TABLE_NAME, null, contentValues);
        } catch (Exception e) {
            XLog.e("DATABASE: insert error");
        }
    }

    // 更新已存在的路线（重命名、重新规划、修改海拔）
    public static void updateHistoryRoute(SQLiteDatabase sqLiteDatabase, String routeID, ContentValues contentValues) {
        try {
            sqLiteDatabase.update(TABLE_NAME, contentValues, DB_COLUMN_ID + " = ?", new String[] {routeID});
        } catch (Exception e) {
            XLog.e("DATABASE: update error");
        }
    }

    // 删除指定的路线
    public static void deleteHistoryRoute(SQLiteDatabase sqLiteDatabase, String routeID) {
        try {
            sqLiteDatabase.delete(TABLE_NAME, DB_COLUMN_ID + " = ?", new String[] {routeID});
        } catch (Exception e) {
            XLog.e("DATABASE: delete error");
        }
    }

    // 清除过期的路线记录
    public static void clearHistoryRoute(SQLiteDatabase sqLiteDatabase, long expireTimestamp) {
        try {
            sqLiteDatabase.delete(TABLE_NAME, DB_COLUMN_TIMESTAMP + " < ?", new String[] {Long.toString(expireTimestamp)});
        } catch (Exception e) {
            XLog.e("DATABASE: clear error");
        }
    }
}
