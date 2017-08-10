package com.roadbump.service;

import android.app.Service;
import android.content.Intent;
import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.DeadObjectException;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.hardware.SensorManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

public class SamplingService extends Service implements SensorEventListener {
  static final String LOG_TAG = "SPEEDBUMPService";
  static final boolean DEBUG_GENERAL = true;
  // Set this to null if you want real accel measurements
  static final String FEED_FILE = null;
  //	static final String FEED_FILE = "speedbump_2013_2_4_8_59_57.csv";

  public int onStartCommand(Intent intent, int flags, int startId) {
    super.onStartCommand(intent, flags, startId);
    Log.d(LOG_TAG, "onStartCommand");
    stopSampling();    // just in case the activity-level service management fails
    rate = SensorManager.SENSOR_DELAY_FASTEST;
    sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
    feedReader = null;
    if (FEED_FILE != null) {
      try {
        AssetManager am = getResources().getAssets();
        feedReader = new BufferedReader(new InputStreamReader(am.open(FEED_FILE)));
      } catch (IOException ex) {
        Log.e(LOG_TAG, ex.getMessage(), ex);
      }
    }
    startSampling();
    Log.d(LOG_TAG, "onStartCommand ends");
    return START_NOT_STICKY;
  }

  public void onDestroy() {
    super.onDestroy();
    Log.d(LOG_TAG, "onDestroy");
    stopSampling();
  }

  public IBinder onBind(Intent intent) {
    return serviceBinder;  // cannot bind
  }

  // SensorEventListener
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
  }

  public void onSensorChanged(SensorEvent sensorEvent) {
    if (sensorEvent.values.length < 3) return;
    long timestamp = sensorEvent.timestamp;
    float x = sensorEvent.values[0];
    float y = sensorEvent.values[1];
    float z = sensorEvent.values[2];
    if (feedReader != null) {
      timestamp = System.currentTimeMillis();
      x = 10.0f;
      y = 0.0f;
      z = 0.0f;
      try {
        String line = feedReader.readLine();
        if (line == null) {
          feedReader.close();
        } else {
          StringTokenizer st = new StringTokenizer(line, ",");
          String s = st.nextToken();  // timestamp
          if (st.countTokens() != 3) {
            s = st.nextToken();
            if (!"sample".equals(s)) {
              throw new NoSuchElementException("not 'sample'");
            }
          }
          s = st.nextToken();
          x = Float.parseFloat(s);
          s = st.nextToken();
          y = Float.parseFloat(s);
          s = st.nextToken();
          z = Float.parseFloat(s);
        }
      } catch (IOException ex) {
      } catch (NoSuchElementException ex) {
      } catch (NumberFormatException ex) {
      }
    }
    if (captureFile != null) {
      captureFile.println(timestamp + "," +
          "sample," +
          x + "," +
          y + "," +
          z);
    }
    processSample(timestamp, x, y, z);
  }

  private void stopSampling() {
    if (!samplingStarted) return;
    if (sensorManager != null) {
      Log.d(LOG_TAG, "unregisterListener/SamplingService");
      sensorManager.unregisterListener(this);
    }
    if (captureFile != null) {
      captureFile.close();
      captureFile = null;
    }
    samplingStarted = false;
  }

  private void startSampling() {
    if (samplingStarted) return;

    initSampling();
    List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
    ourSensor = sensors.size() == 0 ? null : sensors.get(0);

    if (ourSensor != null) {
      Log.d(LOG_TAG, "registerListener/SamplingService");
      sensorManager.registerListener(this, ourSensor, rate);
    }
    captureFile = null;
    if (DEBUG_GENERAL) {
      GregorianCalendar gcal = new GregorianCalendar();
      String fileName = "speedbump_" +
          gcal.get(Calendar.YEAR) +
          "_" +
          Integer.toString(gcal.get(Calendar.MONTH) + 1) +
          "_" +
          gcal.get(Calendar.DAY_OF_MONTH) +
          "_" +
          gcal.get(Calendar.HOUR_OF_DAY) +
          "_" +
          gcal.get(Calendar.MINUTE) +
          "_" +
          gcal.get(Calendar.SECOND) +
          ".csv";
      File captureFileName = new File(Environment.getExternalStorageDirectory(), fileName);
      try {
        captureFile = new PrintWriter(new FileWriter(captureFileName, false));
      } catch (IOException ex) {
        Log.e(LOG_TAG, ex.getMessage(), ex);
      }
    }
    samplingStarted = true;
  }

  private void initSampling() {
    state = STATE_GRAVITYACCEL;
    gravityAccel = 0.0;
    gravityAccelCtr = GRAVITYACCEL_MEAS_LEN;
  }

  private void initMeasuring() {
    lpFilt = new FIR(lp_coeffs);
    bw_0_9_filt = new FIR(bw_0_9);
    bw_1_0_filt = new FIR(bw_1_0);
    bw_1_1_filt = new FIR(bw_1_1);
    pw_0_9 = new PowerWindow(20);
    pw_1_0 = new PowerWindow(20);
    pw_1_1 = new PowerWindow(20);
    pw_0_9_prevstate = false;
    pw_1_0_prevstate = false;
    pw_1_1_prevstate = false;
    state = STATE_MEASURING;
    bumps = 0;
    lastEdge = -1L;
    sampleCounter = 0L;
  }

  // Processes one sample
  private void processSample(long timestamp, float x, float y, float z) {
    double ampl = Math.sqrt((double) x * x +
        (double) y * y +
        (double) z * z);
    switch (state) {
      case STATE_GRAVITYACCEL:
        gravityAccel += ampl;
        --gravityAccelCtr;
        if (gravityAccelCtr <= 0) {
          gravityAccel /= ((double) GRAVITYACCEL_MEAS_LEN);
          initMeasuring();
        }
        break;

      case STATE_MEASURING:
        ampl -= gravityAccel;
        ampl = lpFilt.filter(ampl);
        double a0_9 = bw_0_9_filt.filter(ampl);
        double a1_0 = bw_1_0_filt.filter(ampl);
        double a1_1 = bw_1_1_filt.filter(ampl);
        double pw0_9 = pw_0_9.power(a0_9);
        double pw1_0 = pw_1_0.power(a1_0);
        double pw1_1 = pw_1_1.power(a1_1);
        if (captureFile != null) {
          captureFile.println(timestamp + "," +
              "pws," +
              pw0_9 + "," +
              pw1_0 + "," +
              pw1_1);
        }
        boolean pw_0_9_state = pw0_9 > sensitivity;
        boolean pw_1_0_state = pw1_0 > sensitivity;
        boolean pw_1_1_state = pw1_1 > sensitivity;
        if ((pw_0_9_state && !pw_0_9_prevstate) ||
            (pw_1_0_state && !pw_1_0_prevstate) ||
            (pw_1_1_state && !pw_1_1_prevstate)) {
          Log.d(LOG_TAG, "sampleCounter: "
              + sampleCounter
              + "; 0_9: "
              + pw0_9
              + "; 1_0: "
              + pw1_0
              + "; 1_1: "
              + pw1_1);
          if ((lastEdge < 0L) || ((sampleCounter - lastEdge) > STABLEWINDOW)) {
            if (captureFile != null) {
              captureFile.println(timestamp + "," +
                  "bump," + bumps);
            }
            ++bumps;
          }
          lastEdge = sampleCounter;
        }
        pw_0_9_prevstate = pw_0_9_state;
        pw_1_0_prevstate = pw_1_0_state;
        pw_1_1_prevstate = pw_1_1_state;
        ++sampleCounter;
        break;
    }
  }

  private static final int STABLEWINDOW = 100;
  private static final int GRAVITYACCEL_MEAS_LEN = 50;
  private static final int STATE_GRAVITYACCEL = 1;
  private static final int STATE_MEASURING = 2;
  private int gravityAccelCtr;
  private int state;
  private int rate;
  private SensorManager sensorManager;
  private PrintWriter captureFile;
  private Sensor ourSensor;
  private boolean samplingStarted = false;
  private double sensitivity = 160.0;
  private double gravityAccel = 0.0;
  private FIR lpFilt = null;
  private FIR bw_0_9_filt = null;
  private FIR bw_1_0_filt = null;
  private FIR bw_1_1_filt = null;
  private PowerWindow pw_0_9 = null;
  private PowerWindow pw_1_0 = null;
  private PowerWindow pw_1_1 = null;
  int bumps = 0;
  private long sampleCounter = 0L;
  private long lastEdge = -1L;
  private BufferedReader feedReader;
  private boolean pw_0_9_prevstate;
  private boolean pw_1_0_prevstate;
  private boolean pw_1_1_prevstate;

  private double lp_coeffs[] = {
      0.01607052, 0.04608925, 0.1213877, 0.19989377, 0.23311754, 0.19989377, 0.1213877, 0.04608925,
      0.01607052
  };

  private double bw_0_9[] = {
      -0.172273515143,  // 1
      -0.174359630347,  // 2
      -0.156941022462,  // 3
      -0.171413322493,  // 4
      -0.182708245172,  // 5
      -0.154858014654,  // 6
      0.0368211051541,  // 7
      0.299181883846,  // 8
      0.612604599505,  // 9
      0.844590694689,  // 10
      0.951957180495,  // 11
      0.98326170776,  // 12
      0.966544344336,  // 13
      0.896132396121,  // 14
      0.749224062623,  // 15
      0.53101988164,  // 16
      0.218940712827,  // 17
      -0.106081886451,  // 18
      -0.449622233845,  // 19
      -0.762196574637,  // 20
      -1.02832361851,  // 21
      -1.22699270014,  // 22
      -1.31842287372,  // 23
      -1.33687367091,  // 24
      -1.20708779629,  // 25
      -0.952003464778,  // 26
      -0.607135766917,  // 27
      -0.276258508833,  // 28
      -0.0659763924244,  // 29
      0.0316900850092,  // 30
      0.109616266616,  // 31
      0.237060828005,  // 32
      0.376100322612,  // 33
      0.560930023396,  // 34
      0.726596587088,  // 35
      0.887588345683,  // 36
      1.05972288488,  // 37
      1.14271942995,  // 38
      1.13530112361,  // 39
      1.02473357604,  // 40
      0.825365249319,  // 41
      0.550451823159,  // 42
      0.246930978159,  // 43
      -0.0743134376626,  // 44
      -0.388292163052,  // 45
      -0.612371360461,  // 46
      -0.744031699368,  // 47
      -0.753610748178,  // 48
      -0.676243904224,  // 49
      -0.556387309343,  // 50
      -0.450310701591,  // 51
      -0.424882439796,  // 52
      -0.441944574605,  // 53
      -0.451396174286,  // 54
      -0.379596577916,  // 55
      -0.231186208235,  // 56
      -0.0580414255175,  // 57
      0.098283680118,  // 58
      0.223143766706,  // 59
      0.349729247309,  // 60
      0.463047364674,  // 61
      0.576268451225,  // 62
      0.679609809072,  // 63
      0.760889850698,  // 64
      0.835371248315,  // 65
      0.901547867372,  // 66
      1.04156395595,  // 67
      1.30789211351,  // 68
      1.67791912512,  // 69
      2.12326482386,  // 70
      2.57959607298,  // 71
      2.96145384227,  // 72
      3.21654802656,  // 73
      3.31236166183,  // 74
      3.17454319227,  // 75
      2.79439018245,  // 76
      2.1832508503,  // 77
      1.31042989425,  // 78
      0.28399649234,  // 79
      -0.729028789763,  // 80
      -1.59157885988,  // 81
      -2.2260139335,  // 82
      -2.64219628033,  // 83
      -2.88096084083,  // 84
      -3.0082188697,  // 85
      -3.055773297,  // 86
      -3.0622096391,  // 87
      -3.02750849458,  // 88
      -2.87948933638,  // 89
      -2.55552716802,  // 90
      -2.0355472583,  // 91
      -1.35189968486,  // 92
      -0.551312089037,  // 93
      0.264016252934,  // 94
      0.996688152685,  // 95
      1.58124291216,  // 96
      1.95671672212,  // 97
      2.1012330247,  // 98
      2.03187946992,  // 99
      1.77939476283,  // 100
      1.36507417677,  // 101
      0.863321478415,  // 102
      0.367280628445,  // 103
      -0.0442535085751,  // 104
      -0.322559709659,  // 105
      -0.510131874379,  // 106
      -0.664964593137,  // 107
      -0.802843579485,  // 108
      -0.938486627901,  // 109
      -1.07386103198,  // 110
      -1.20261883491,  // 111
      -1.30748620179,  // 112
      -1.35475830781,  // 113
      -1.33964165242,  // 114
      -1.28234678798,  // 115
      -1.23357517304,  // 116
      -1.20173180435,  // 117
      -1.15656423439,  // 118
      -1.08999294043,  // 119
      -0.993403523015,  // 120
      -0.887041728287,  // 121
      -0.829209276205,  // 122
      -0.860339727146,  // 123
      -0.965105701563,  // 124
      -1.10767132624,  // 125
      -1.20735409448,  // 126
      -1.19909118134,  // 127
      -1.12703897319,  // 128
      -1.06300757187,  // 129
      -0.988150342978,  // 130
      -0.903112413969,  // 131
      -0.814756372579,  // 132
      -0.663813419302,  // 133
      -0.554303561472,  // 134
      -0.503693819385,  // 135
      -0.338354259663,  // 136
      -0.0183736444437,  // 137
      0.410547458526,  // 138
      0.920349069012,  // 139
      1.40870573067,  // 140
      1.78314217707,  // 141
      2.12420864063,  // 142
      2.42852970132,  // 143
      2.63849870297,  // 144
      2.78154615055,  // 145
      2.82338728951,  // 146
      2.71784694128,  // 147
      2.43089897635,  // 148
      1.94132171545,  // 149
      1.26621414469,  // 150
      0.508944744895,  // 151
      -0.214528497025,  // 152
      -0.887049562081,  // 153
      -1.45905067365,  // 154
      -1.9046204139,  // 155
      -2.23265151759,  // 156
      -2.39304497619,  // 157
      -2.47012637986,  // 158
      -2.52065754303,  // 159
      -2.4860719504,  // 160
      -2.29173186382,  // 161
      -1.85540912364,  // 162
      -1.2281329474,  // 163
      -0.543159682268,  // 164
      0.0460897112885,  // 165
      0.564144944276,  // 166
      1.04096121302,  // 167
      1.42811996734,  // 168
      1.74978412294,  // 169
      2.00321687234,  // 170
      2.21007244398,  // 171
      2.34308005459,  // 172
      2.40014337473,  // 173
      2.30800226568,  // 174
      2.08101788134,  // 175
      1.80413808734,  // 176
      1.46042926849,  // 177
      1.12068568878,  // 178
      0.778538840535,  // 179
      0.462924408912,  // 180
      0.154909010489,  // 181
      -0.181235512647,  // 182
      -0.508659913404,  // 183
      -0.790605873385,  // 184
      -0.984866850789,  // 185
      -1.10908334378,  // 186
      -1.14934912848,  // 187
      -1.18096539586,  // 188
      -1.21867358613,  // 189
      -1.2274599422,  // 190
      -1.21148957038,  // 191
      -1.15525583694,  // 192
      -1.06095390998,  // 193
      -0.940351728138,  // 194
      -0.822159719183,  // 195
      -0.671293291938,  // 196
      -0.496762474902,  // 197
      -0.326459225321,  // 198
      -0.176562418616,  // 199
      -0.0553750262603,  // 200
      0.0482236262718,  // 201
      0.149422055471,  // 202
      0.255707193138,  // 203
      0.34050234201,  // 204
      0.38851512844,  // 205
      0.402361775571,  // 206
      0.397425359309,  // 207
      0.370221783784,  // 208
      0.36983630014,  // 209
      0.411575681193,  // 210
      0.48388279313,  // 211
      0.558060924401,  // 212
      0.582424268229,  // 213
      0.5220653223,  // 214
      0.394033089959,  // 215
      0.263150207134,  // 216
      0.124114058621,  // 217
      0.0346150535218,  // 218
      -0.0385905560682,  // 219
      -0.0771422459308,  // 220
      -0.104368452892,  // 221
      -0.128568380414  // 222
  };

  private double bw_1_0[] = {
      -0.172273515143,  // 1
      -0.168371991205,  // 2
      -0.162410529796,  // 3
      -0.171443738733,  // 4
      -0.187765226067,  // 5
      -0.0634481264135,  // 6
      0.205511673859,  // 7
      0.543497696093,  // 8
      0.826276620579,  // 9
      0.951957180495,  // 10
      0.982959161781,  // 11
      0.957726766452,  // 12
      0.8538112948,  // 13
      0.664585851096,  // 14
      0.364223642308,  // 15
      0.00248751772451,  // 16
      -0.372503277598,  // 17
      -0.730683470248,  // 18
      -1.02832361851,  // 19
      -1.24140176952,  // 20
      -1.33061354333,  // 21
      -1.31190299441,  // 22
      -1.1058923847,  // 23
      -0.768271288857,  // 24
      -0.375363528589,  // 25
      -0.101722081676,  // 26
      0.0243887598416,  // 27
      0.109616266616,  // 28
      0.251762792532,  // 29
      0.413270108738,  // 30
      0.622175682179,  // 31
      0.792685516174,  // 32
      0.989747779522,  // 33
      1.12602341854,  // 34
      1.14518891641,  // 35
      1.04192807332,  // 36
      0.825365249319,  // 37
      0.517028571688,  // 38
      0.178210836421,  // 39
      -0.185028304486,  // 40
      -0.499873298558,  // 41
      -0.698347046194,  // 42
      -0.763934056601,  // 43
      -0.697636894215,  // 44
      -0.570887809997,  // 45
      -0.450310701591,  // 46
      -0.425645546951,  // 47
      -0.447036887808,  // 48
      -0.439015115756,  // 49
      -0.319266370938,  // 50
      -0.13586769755,  // 51
      0.0508204014895,  // 52
      0.195829173175,  // 53
      0.335955087664,  // 54
      0.463047364674,  // 55
      0.589016486132,  // 56
      0.698581589406,  // 57
      0.787260948051,  // 58
      0.862834876557,  // 59
      0.964046789259,  // 60
      1.20625797343,  // 61
      1.58785593383,  // 62
      2.07170200424,  // 63
      2.57959607298,  // 64
      2.99646596659,  // 65
      3.25378434032,  // 66
      3.29537086651,  // 67
      3.03275431743,  // 68
      2.48674649303,  // 69
      1.62711841722,  // 70
      0.51616606557,  // 71
      -0.621553712646,  // 72
      -1.59157885988,  // 73
      -2.2825363882,  // 74
      -2.70830885244,  // 75
      -2.93334840782,  // 76
      -3.03764704076,  // 77
      -3.06195765667,  // 78
      -3.04777975628,  // 79
      -2.92577654465,  // 80
      -2.60143958138,  // 81
      -2.0355472583,  // 82
      -1.26744071787,  // 83
      -0.367052381676,  // 84
      0.520607106419,  // 85
      1.27899356025,  // 86
      1.8180232396,  // 87
      2.07844756896,  // 88
      2.06386263867,  // 89
      1.81607894977,  // 90
      1.36507417677,  // 91
      0.806480516197,  // 92
      0.265740346313,  // 93
      -0.151542147359,  // 94
      -0.412577112835,  // 95
      -0.598711732527,  // 96
      -0.757964850928,  // 97
      -0.908046743831,  // 98
      -1.05909591885,  // 99
      -1.20261883491,  // 100
      -1.31597265311,  // 101
      -1.35649156222,  // 102
      -1.32310850065,  // 103
      -1.25710897812,  // 104
      -1.21617829256,  // 105
      -1.17373411327,  // 106
      -1.1071882432,  // 107
      -1.0054100256,  // 108
      -0.887041728287,  // 109
      -0.828170791105,  // 110
      -0.878270138114,  // 111
      -1.01123011489,  // 112
      -1.16321255584,  // 113
      -1.21570320853,  // 114
      -1.15233851066,  // 115
      -1.07635299198,  // 116
      -0.997894319733,  // 117
      -0.903112413969,  // 118
      -0.800952433775,  // 119
      -0.630449402071,  // 120
      -0.539159158435,  // 121
      -0.451321618184,  // 122
      -0.176291506588,  // 123
      0.256901294314,  // 124
      0.803496631477,  // 125
      1.36005967062,  // 126
      1.78314217707,  // 127
      2.16168458369,  // 128
      2.48268439912,  // 129
      2.69359817682,  // 130
      2.81617675311,  // 131
      2.7850135648,  // 132
      2.54862642254,  // 133
      2.06767148433,  // 134
      1.34802999432,  // 135
      0.508944744896,  // 136
      -0.29190734323,  // 137
      -1.02558760579,  // 138
      -1.61965303118,  // 139
      -2.06855498595,  // 140
      -2.34075027636,  // 141
      -2.4461168248,  // 142
      -2.51474607216,  // 143
      -2.49588265206,  // 144
      -2.29173186382,  // 145
      -1.7931611128,  // 146
      -1.07480986014,  // 147
      -0.332522110012,  // 148
      0.278875592625,  // 149
      0.83922408614,  // 150
      1.30731350892,  // 151
      1.68437398345,  // 152
      1.97750330566,  // 153
      2.21007244398,  // 154
      2.35335936178,  // 155
      2.39610786254,  // 156
      2.24009365061,  // 157
      1.96611396154,  // 158
      1.61736778184,  // 159
      1.23227492609,  // 160
      0.854566351883,  // 161
      0.495998490225,  // 162
      0.154909010489,  // 163
      -0.21840029266,  // 164
      -0.577707626938,  // 165
      -0.864224533584,  // 166
      -1.04977546547,  // 167
      -1.13912760412,  // 168
      -1.16692746993,  // 169
      -1.21292437673,  // 170
      -1.22743518554,  // 171
      -1.21148957038,  // 172
      -1.14673479296,  // 173
      -1.03495071157,  // 174
      -0.901445145169,  // 175
      -0.760752790971,  // 176
      -0.574477192789,  // 177
      -0.38217657762,  // 178
      -0.20705105306,  // 179
      -0.0678448862243,  // 180
      0.0482236262718,  // 181
      0.161347479314,  // 182
      0.277188784526,  // 183
      0.361100956665,  // 184
      0.397434052573,  // 185
      0.402632629845,  // 186
      0.379588198091,  // 187
      0.365493709144,  // 188
      0.40544652355,  // 189
      0.48388279313,  // 190
      0.563833343556,  // 191
      0.577575237089,  // 192
      0.48242536066,  // 193
      0.336592930462,  // 194
      0.183864391628,  // 195
      0.0585568031706,  // 196
      -0.022153330035,  // 197
      -0.0758848798766,  // 198
      -0.104368452892,  // 199
      -0.122069417431,  // 200
  };

  private double bw_1_1[] = {
      -0.172273515143,  // 1
      -0.162973269537,  // 2
      -0.167390493097,  // 3
      -0.175110508235,  // 4
      -0.166257654592,  // 5
      0.0636833551072,  // 6
      0.40100859067,  // 7
      0.760346717751,  // 8
      0.935198343985,  // 9
      0.98326170776,  // 10
      0.957726766452,  // 11
      0.838018962189,  // 12
      0.615437852267,  // 13
      0.255476891758,  // 14
      -0.142967023494,  // 15
      -0.561321256555,  // 16
      -0.913309476555,  // 17
      -1.19349546404,  // 18
      -1.31842287372,  // 19
      -1.32239268901,  // 20
      -1.1058923847,  // 21
      -0.728621036524,  // 22
      -0.307713135791,  // 23
      -0.0503216292975,  // 24
      0.0529532194003,  // 25
      0.177715391652,  // 26
      0.342161813741,  // 27
      0.560930023396,  // 28
      0.759010443075,  // 29
      0.969903906759,  // 30
      1.12602341854,  // 31
      1.14090797747,  // 32
      1.00649556917,  // 33
      0.741026347732,  // 34
      0.382268981274,  // 35
      -0.000403080349741,  // 36
      -0.388292163052,  // 37
      -0.650044543391,  // 38
      -0.763860838633,  // 39
      -0.707538263169,  // 40
      -0.570887809997,  // 41
      -0.443524053984,  // 42
      -0.428307017929,  // 43
      -0.452807346704,  // 44
      -0.404275241758,  // 45
      -0.231186208235,  // 46
      -0.0200807642556,  // 47
      0.155187231088,  // 48
      0.30783384339,  // 49
      0.451007361269,  // 50
      0.589016486132,  // 51
      0.70770958181,  // 52
      0.804276489455,  // 53
      0.884383970583,  // 54
      1.04156395595,  // 55
      1.38198506112,  // 56
      1.86933149262,  // 57
      2.43167295681,  // 58
      2.92483100381,  // 59
      3.2361436737,  // 60
      3.29537086651,  // 61
      2.99037236633,  // 62
      2.34179445835,  // 63
      1.31042989425,  // 64
      0.0526366179546,  // 65
      -1.13809061218,  // 66
      -2.04013462786,  // 67
      -2.60580966481,  // 68
      -2.89962959615,  // 69
      -3.0317180387,  // 70
      -3.06195765667,  // 71
      -3.04227049194,  // 72
      -2.87948933638,  // 73
      -2.45623491064,  // 74
      -1.7497447346,  // 75
      -0.826014364147,  // 76
      0.176250728713,  // 77
      1.07024125395,  // 78
      1.73160070871,  // 79
      2.06530277064,  // 80
      2.06386263867,  // 81
      1.77939476283,  // 82
      1.25747409152,  // 83
      0.63751964585,  // 84
      0.0788796800858,  // 85
      -0.297527281105,  // 86
      -0.528416795799,  // 87
      -0.712252489984,  // 88
      -0.877746803396,  // 89
      -1.0442519196,  // 90
      -1.20261883491,  // 91
      -1.32366845796,  // 92
      -1.35520917914,  // 93
      -1.30320650546,  // 94
      -1.23758656086,  // 95
      -1.19765577581,  // 96
      -1.13719654497,  // 97
      -1.03987093598,  // 98
      -0.908827329448,  // 99
      -0.829209276205,  // 100
      -0.878270138114,  // 101
      -1.02730404596,  // 102
      -1.18487920307,  // 103
      -1.20481206635,  // 104
      -1.11901258963,  // 105
      -1.04144952747,  // 106
      -0.938834879941,  // 107
      -0.839149862903,  // 108
      -0.663813419302,  // 109
      -0.543639714162,  // 110
      -0.451321618184,  // 111
      -0.138929184096,  // 112
      0.358185145528,  // 113
      0.978553656507,  // 114
      1.54459209393,  // 115
      1.97207449448,  // 116
      2.36904975174,  // 117
      2.63849870297,  // 118
      2.80176177714,  // 119
      2.79657621752,  // 120
      2.54862642254,  // 121
      2.00572068413,  // 122
      1.18334563673,  // 123
      0.261853920015,  // 124
      -0.595763665259,  // 125
      -1.34388946489,  // 126
      -1.9046204139,  // 127
      -2.28263642074,  // 128
      -2.42971481859,  // 129
      -2.51033871428,  // 130
      -2.49588265206,  // 131
      -2.25556179045,  // 132
      -1.66211439424,  // 133
      -0.843247848419,  // 134
      -0.0746866287218,  // 135
      0.564144944275,  // 136
      1.13420591327,  // 137
      1.57931260384,  // 138
      1.92465343773,  // 139
      2.19013071481,  // 140
      2.35335936178,  // 141
      2.39069412276,  // 142
      2.18922605833,  // 143
      1.87208045626,  // 144
      1.46042926849,  // 145
      1.04560080998,  // 146
      0.632369371806,  // 147
      0.261762181569,  // 148
      -0.143908601351,  // 149
      -0.543487936623,  // 150
      -0.864224533584,  // 151
      -1.0638425603,  // 152
      -1.14495616145,  // 153
      -1.18096539586,  // 154
      -1.22266919,  // 155
      -1.22472662665,  // 156
      -1.17818109132,  // 157
      -1.07339318538,  // 158
      -0.927242831891,  // 159
      -0.777012594833,  // 160
      -0.574477192789,  // 161
      -0.36342182812,  // 162
      -0.176562418616,  // 163
      -0.0310668631613,  // 164
      0.0920083505018,  // 165
      0.221331547032,  // 166
      0.332701528989,  // 167
      0.391337741085,  // 168
      0.403203191793,  // 169
      0.382997425516,  // 170
      0.365493709144,  // 171
      0.411575681193,  // 172
      0.502422473337,  // 173
      0.576935070201,  // 174
      0.553213389202,  // 175
      0.408728422432,  // 176
      0.247630549616,  // 177
      0.0874025934327,  // 178
      -0.00517071844083,  // 179
      -0.0741553079916,  // 180
      -0.104368452892,  // 181
  };

  class FIR {
    public FIR(double coeffs[]) {
      this.coeffs = coeffs;
      filter_len = coeffs.length;
      filter_buf = new double[filter_len];
      buf_ptr = 0;
      for (int i = 0; i < filter_len; ++i)
        filter_buf[i] = 0.0;
    }

    public double filter(double inp) {
      filter_buf[buf_ptr] = inp;
      int tmp_ptr = buf_ptr;
      double mac = 0.0;
      for (int i = 0; i < filter_len; ++i) {
        mac = mac + coeffs[i] * filter_buf[tmp_ptr];
        --tmp_ptr;
        if (tmp_ptr < 0) tmp_ptr = filter_len - 1;
      }
      buf_ptr = (++buf_ptr) % filter_len;
      return mac;
    }

    double coeffs[];
    double filter_buf[];
    int buf_ptr;
    int filter_len;
  }

  class PowerWindow {
    public PowerWindow(int len) {
      filter_len = len;
      filter_buf = new double[filter_len];
      buf_ptr = 0;
      for (int i = 0; i < filter_len; ++i)
        filter_buf[i] = 0.0;
    }

    public double power(double inp) {
      filter_buf[buf_ptr] = inp;
      int tmp_ptr = buf_ptr;
      double mac = 0.0;
      for (int i = 0; i < filter_len; ++i) {
        mac = mac + filter_buf[tmp_ptr] * filter_buf[tmp_ptr];
        --tmp_ptr;
        if (tmp_ptr < 0) tmp_ptr = filter_len - 1;
      }
      mac = mac / (double) filter_len;
      mac = Math.sqrt(mac);
      buf_ptr = (++buf_ptr) % filter_len;
      return mac;
    }

    double filter_buf[];
    int buf_ptr;
    int filter_len;
  }
}

