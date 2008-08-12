package org.codec.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class WavPlayer {
	
    private static final int EXTERNAL_BUFFER_SIZE = 128000;
    
    public static void PlayWav(final InputStream inputStream) {
    	 AudioInputStream audioInputStream = null;
         try {
             audioInputStream = AudioSystem.getAudioInputStream(inputStream);
         }
         catch (Exception e) {
             e.printStackTrace();
             System.exit(1);
         }

         AudioFormat audioFormat = audioInputStream.getFormat();
         SourceDataLine line = null;

         DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
         try {
             line = (SourceDataLine) AudioSystem.getLine(info);
             line.open(audioFormat);
         }
         catch (LineUnavailableException e) {
             e.printStackTrace();
             System.exit(1);
         }
         catch (Exception e) {
             e.printStackTrace();
             System.exit(1);
         }

         line.start();

         int nBytesRead = 0;
         byte[] abData = new byte[EXTERNAL_BUFFER_SIZE];
         while (nBytesRead != -1) {
             try {
                 nBytesRead = audioInputStream.read(abData, 0, abData.length);
             }
             catch (IOException e) {
                 e.printStackTrace();
             }
             if (nBytesRead >= 0) {
                 int nBytesWritten = line.write(abData, 0, nBytesRead);
             }
         }

         line.drain();
         line.close();
    }

    //should eventually be removed
    public static void PlayWav(final File soundFile) {
    	InputStream inputStream;
		try {
			inputStream = new FileInputStream(soundFile);
			PlayWav(inputStream);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	
       
    }

}