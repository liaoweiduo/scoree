package com.sustech.se.scoree.UI;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.os.AsyncTask;
import android.widget.Toast;

import com.sustech.se.scoree.Data;
import com.sustech.se.scoree.FileOperator;
import com.sustech.se.scoree.R;
import com.sustech.se.scoree.Song;
import com.sustech.se.scoree.Note;
import com.sustech.se.scoree.audioCapturer.AudioCapturerInterface;
import com.sustech.se.scoree.audioProcesser.Decoder;
import com.sustech.se.scoree.audioProcesser.DecoderInterface;
import com.sustech.se.scoree.audioProcesser.Detector;
import com.sustech.se.scoree.audioProcesser.DetectorInterface;


public class showActivity extends AppCompatActivity {           //改为 Intent进来 putExtra是Song

    public static final String SONG = "com.sustech.se.scoree.SONG";
    private int maxOfFrame; //界面中frame的最大容量
    private FrameLayout frame_staff[]; //存放每行乐谱图片的famelayout
    private TextView currentNote;
    private Song staff; //乐谱
    private String staffName;
    private View indicator; //标识匹配的竖杆
    private float scale;
    private int currentMatchingNote; //当前匹配到的音符序号
    private int currentLine; //
    private int pastLine;
    private final int indicatorY = 30; //indicator显示位置的纵坐标
    private int numOfLineShown; //界面设置的显示的乐谱行数
    private int currentStaffLine; //表示当前界面显示最后一行谱所对应的实际乐谱行序号
    private int lineNumToChange; //设置的换谱行数

    private static final String PERMISSION_AUDIO="android.permission.RECORD_AUDIO";
    private Data gData;
    private AudioCapturerInterface ac;
    private DetectorInterface detector;
    private DecoderInterface decoder;
    AudioAsyncTask audio = null;
    private Button button;
    private boolean started = false;
    private String filePath;
    //private int grade;
    private boolean flag = true;
    private int grade[];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show);
        gData = ((Data)getApplicationContext());
        Intent intent = getIntent();
        staffName =intent.getStringExtra(SONG);
        Log.i("showActivity","staff:" + staffName);
        numOfLineShown = gData.getNumOfLines() + 1;
        lineNumToChange = gData.getPageTurnSetting();
        maxOfFrame = 6;
        scale = 1.5f;
        currentMatchingNote = 0;
        currentLine = 0;
        pastLine = -1;
        currentStaffLine = -1;

        initialFrame_staff(numOfLineShown);
        initialIndicator();
        grade = new int[staff.getNumOfNotes()];
        for(int i=0;i<staff.getNumOfNotes();i++){
            grade[i] = 0;
        }

        currentNote = (TextView)findViewById(R.id.editText_currentNote);

        detector = new Detector(gData.getAudioCapturerConfig().getBUFFER_SIZE());
        decoder= new Decoder(gData.getAudioCapturerConfig().getBUFFER_SIZE(), gData.getAudioCapturerConfig().getSAMPLE_RATE());

        ac=gData.getAudioCapturer();

        button = (Button) findViewById(R.id.audioButton);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (started){//cancel
                    ac.stopCapture();
                    audio.cancel(true);
                    button.setText(R.string.start);
                    started = false;
                }
                else {
                    int checkPermission=checkCallingOrSelfPermission(PERMISSION_AUDIO);
                    if(checkPermission!= PackageManager.PERMISSION_GRANTED){
                        Log.e("MainActivity","No permission for audio");
//                        requestPermissions(new String[]{PERMISSION_AUDIO}, 0);
                        return;
                    }
                    ac.startCapture();
                    audio = new AudioAsyncTask();
                    audio.execute();

                    button.setText(R.string.stop);
                    started = true;
                    if(currentMatchingNote==staff.getNumOfNotes()){
                        TextView setGrade = (TextView)findViewById(R.id.textView_grade);
                        setGrade.setText(String.valueOf(calGrade() * 100 / grade.length));
                    }

                }
            }
        });
    }

    public int calGrade(){
        int countTrue = 0;
        for(int temp: grade){
            if(temp == 1)
                countTrue++;
        }
        return countTrue;
    }
    //匹配识别到的琴键
    private void match(int key){

        if(staff.getNumOfNotes()>currentMatchingNote){
            Note note = staff.getNoteById(currentMatchingNote);
            currentLine = note.getLineNum();
            if(pastLine<currentLine){
                if(currentLine>lineNumToChange&&(currentLine%lineNumToChange==1)){
                    chageFrame_staff(lineNumToChange);
                }
                pastLine = currentLine;
                indicator.setVisibility(View.INVISIBLE);
                initialIndicator();
                //frame_staff[(pastLine%numOfLineShown)].removeView(indicator);
                int index;
                if(currentLine>=numOfLineShown){
                    index = currentLine%numOfLineShown+1;

                }
                else{
                    index = currentLine;
                }
                frame_staff[index].addView(indicator);
                //pastLine = currentLine;
            }
            //currentNote.setText(String.valueOf(note.getPitch()));
            if(note.getPitch() != key){
                indicator.setBackgroundColor(getResources().getColor(R.color.colorAccent));
                //++currentMatchingNote;
                currentNote.setText(mapNote(note.getPitch()));
                flag = false;
            }
            else{
                indicator.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                if(flag)
                    grade[currentMatchingNote] = 1;
                flag = true;
                ++currentMatchingNote;

                TextView setGrade = (TextView)findViewById(R.id.textView_grade);
                if (currentMatchingNote == 0){
                    setGrade.setText("0");
                } else {
                    setGrade.setText(String.valueOf(calGrade())+"/"+String.valueOf(staff.getNumOfNotes()));
                }
            }
            indicator.setX(note.getPosition()*scale);
            indicator.setY(indicatorY*scale);

        }
        else{
            //Toast.makeText(this, "Matching done", Toast.LENGTH_SHORT).show();
        }

    }

    //切换谱
    private void chageFrame_staff(int lineNumToChange){
        for(int i=1; i<lineNumToChange+1;i++){
            staff.getImgOfStaff(i).setVisibility(View.INVISIBLE);
            //frame_staff[i].removeAllViews();
        }
        int n = staff.getNumOfLine()-currentStaffLine;
        for(int i=1; i<=n&&i<numOfLineShown; i++){
            currentStaffLine = currentStaffLine+1;
            frame_staff[i].addView(staff.getImgOfStaff(currentStaffLine));
            staff.getImgOfStaff(currentStaffLine).setScaleType(ImageView.ScaleType.FIT_START);
        }
    }

    private void initialFrame_staff(int numOfLineShown){
        initialFrame_staff();
        initialStaff();
        int i=0;
        for(i=0; i<numOfLineShown&&i<staff.getNumOfLine()+1; i++){
            frame_staff[i].addView(staff.getImgOfStaff(i));
            staff.getImgOfStaff(i).setScaleType(ImageView.ScaleType.FIT_START);
        }
        currentStaffLine = numOfLineShown-1;
        if(numOfLineShown< maxOfFrame){
            for(i=numOfLineShown; i< maxOfFrame; i++){
                frame_staff[i].setVisibility(View.INVISIBLE);
            }
        }
    }

    private void initialFrame_staff(){
        frame_staff = new FrameLayout[maxOfFrame];
        frame_staff[0] = (FrameLayout)findViewById(R.id.frame_title);
        frame_staff[1] = (FrameLayout)findViewById(R.id.frame_first_line);
        frame_staff[2] = (FrameLayout)findViewById(R.id.frame_second_line);
        frame_staff[3] = (FrameLayout)findViewById(R.id.frame_third_line);
        frame_staff[4] = (FrameLayout)findViewById(R.id.frame_fourth_line);
        frame_staff[5] = (FrameLayout)findViewById(R.id.frame_fifth_line);
    }

    public void initialStaff(){

        filePath = "/" + gData.getWorkingDirectory();
        staff = FileOperator.getSongFromInputStream(
                getExternalFilesDir(null).getAbsolutePath() + filePath + "/" + staffName + "/" + staffName + ".txt");
        int numOfImg = staff.getNumOfLine()+1;
        String[] names = new String[numOfImg];
        for(int i=0; i<numOfImg; i++){
            names[i] = filePath + "/" + staff.getFilename() + "/" + staff.getFilename() + "_" + i + ".png";
        }
        ImageView imgs[] = new ImageView[numOfImg];
        //imgs[0] = (ImageView)findViewById(R.id.imageView);
        for(int i=0; i<numOfImg; i++){
            imgs[i] = new ImageView(this);
            imgs[i].setImageBitmap(FileOperator.getLoacalBitmap(this, getExternalFilesDir(null).getAbsolutePath() + names[i]));
        }

        staff.setImgOfStaffs(imgs);
    }

    private void initialIndicator(){
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(0,0);
        indicator = new View(this);
        params.height = 100;
        params.width = 8;
        indicator.setLayoutParams(params);
        //indicator.setBackgroundColor(getResources().getColor(R.color.black));
        //frame_staff[1].addView(indicator);
    }

    private class AudioAsyncTask extends AsyncTask<Void, Integer, Void> {

        @Override
        protected Void doInBackground(Void... datas) {
            while(ac.isCaptureStarted()) {
                short[] buffer = ac.read();
                if (buffer == null) return null;
                double[] detectResult = detector.detect(buffer);
                int key = decoder.decode(detectResult);
                if (detectResult != null) publishProgress(key);
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... keys) {
            int key = keys[0]+25;
            match(key);

        }

        @Override
        protected void onPostExecute(Void result) {
            Log.i("mainActivity", "onPostExecute() called");
        }

        //onCancelled方法用于在取消执行中的任务时更改UI
        @Override
        protected void onCancelled() {
            Log.i("mainActivity", "onCancelled() called");
        }
    }

    public String mapNote(int num){

        String map = null;
        switch(num%12){
            case 1: map = "A";break;
            case 2: map = "A#";break;
            case 3: map = "B"; break;
            case 4: map = "C"; break;
            case 5: map = "C#"; break;
            case 6: map = "D"; break;
            case 7: map = "D#"; break;
            case 8: map = "E"; break;
            case 9: map = "F"; break;
            case 10: map = "F#"; break;
            case 11: map = "G"; break;
            case 0: map = "G#"; break;
        }
        if(map.equals("A")||map.equals("A#")||map.equals("B")){
            map += Integer.toString(num/12);
        }
        else{
            map += Integer.toString(num/12+1);
        }
        return map;
    }
}
