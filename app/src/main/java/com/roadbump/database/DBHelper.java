package com.roadbump.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by harshikesh.kumar on 24/09/16.
 */
public class DBHelper extends SQLiteOpenHelper {

  public static final String DATABASE_NAME = "bump.db";
  private static final int DATABASE_VERSION = 1;
  public static final String BUMP_TABLE_NAME = "bump_table";
  public static final String BUMP_COLUMN_ID = "_id";
  public static final String BUMP_ACC_X_VALUE = "acc_xvalue";
  public static final String BUMP_ACC_Y_VALUE = "acc_yvalue";
  public static final String BUMP_ACC_Z_VALUE = "acc_zvalue";
  public static final String BUMP_LATITUDE = "latitude";
  public static final String BUMP_LONGITUDE = "longitude";

  public DBHelper(Context context) {
    super(context, DATABASE_NAME , null, DATABASE_VERSION);
  }

  @Override public void onCreate(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE " + BUMP_TABLE_NAME + "(" +
        BUMP_COLUMN_ID + " INTEGER PRIMARY KEY, " +
        BUMP_LATITUDE + " REAL unique, " +
        BUMP_LONGITUDE + " REAL unique, " +
        BUMP_ACC_Y_VALUE + " REAL, " +
        BUMP_ACC_Z_VALUE + " REAL, " +
        BUMP_ACC_X_VALUE + " REAL)"
    );
  }

  @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    db.execSQL("DROP TABLE IF EXISTS " + BUMP_TABLE_NAME);
    onCreate(db);
  }

  public boolean insertData(double latitude, double longitude, double xacc, double yacc, double zacc) {
    SQLiteDatabase db = getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(BUMP_LATITUDE, latitude);
    contentValues.put(BUMP_LONGITUDE, longitude);
    contentValues.put(BUMP_ACC_X_VALUE, xacc);
    contentValues.put(BUMP_ACC_Y_VALUE, yacc);
    contentValues.put(BUMP_ACC_Z_VALUE, zacc);
    db.insertWithOnConflict(BUMP_TABLE_NAME, null, contentValues,SQLiteDatabase.CONFLICT_REPLACE);
    return true;
  }

  public boolean updateData(int id ,double latitude, double longitude, double xacc) {
    SQLiteDatabase db = this.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(BUMP_LATITUDE, latitude);
    contentValues.put(BUMP_LONGITUDE, longitude);
    contentValues.put(BUMP_ACC_X_VALUE, xacc);
    db.update(BUMP_TABLE_NAME, contentValues, BUMP_COLUMN_ID + " = ? ", new String[] { Integer.toString(id) } );
    return true;
  }

  public Cursor getAllData() {
    SQLiteDatabase db = this.getReadableDatabase();
    Cursor res = db.rawQuery( "SELECT * FROM " + BUMP_TABLE_NAME, null );
    return res;
  }

}
