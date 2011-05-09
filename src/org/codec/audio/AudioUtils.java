package org.codec.audio;

import javax.sound.sampled.*;

import org.codec.audio.common.AudioBuffer;
import org.codec.audio.common.AudioEncoder;
import org.codec.audio.common.StreamDecoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Copyright 2002 by the authors. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author Crista Lopes (lopes at uci dot edu)
 * @version 1.0
 */

public class AudioUtils {

    //the default format for reading and writing audio information
    public static AudioFormat kDefaultFormat = new AudioFormat((float) AudioEncoder.kSamplingFrequency,
                                                               (int) 8, (int) 1, true, false);

    public static void decodeWavFile(File inputFile, OutputStream out) throws UnsupportedAudioFileException, IOException {
        StreamDecoder decoder = new StreamDecoder(out);

        AudioBuffer decoderBuffer = decoder.getAudioBuffer();

        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(kDefaultFormat,
                                                                            AudioSystem.getAudioInputStream(
                                                                                    inputFile
                                                                            ));
        int bytesPerFrame = audioInputStream.getFormat().getFrameSize();

        // Set an arbitrary buffer size of 1024 frames.
        int numBytes = 60000 * bytesPerFrame;
        byte[] audioBytes = new byte[numBytes];
        int numBytesRead = 0;
        
        // Try to read numBytes bytes from the file and write it to the buffer
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while ((numBytesRead = audioInputStream.read(audioBytes)) != -1) {
            /*
               for(int i=0; i < numBytesRead; i++){
               float val = audioBytes[i] / (float)org.codec.utils.Constants.kFloatToByteShift;
               //System.out.println("" + val);
               }
             */
            decoderBuffer.write(audioBytes, 0, numBytesRead);
        }
        decoder.end();
//        try {
//            decoder.getMyThread().join();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        System.out.println("END DECODE WAW FILE");
//        audioInputStream.close();
//        decoder.quit();
    }

    public static void decodeWavFile2(byte[] input, OutputStream out) throws UnsupportedAudioFileException, IOException {
        StreamDecoder decoder = new StreamDecoder(out);

        AudioBuffer decoderBuffer = decoder.getAudioBuffer();

        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(kDefaultFormat,
                                                                            AudioSystem.getAudioInputStream(
                                                                                    new ByteArrayInputStream(input)
                                                                            ));
        int bytesPerFrame = audioInputStream.getFormat().getFrameSize();

        // Set an arbitrary buffer size of 1024 frames.
        int numBytes = 60000 * bytesPerFrame;
        byte[] audioBytes = new byte[numBytes];
        int numBytesRead = 0;

        // Try to read numBytes bytes from the file and write it to the buffer
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while ((numBytesRead = audioInputStream.read(audioBytes)) != -1) {
            /*
               for(int i=0; i < numBytesRead; i++){
               float val = audioBytes[i] / (float)org.codec.utils.Constants.kFloatToByteShift;
               //System.out.println("" + val);
               }
             */
            decoderBuffer.write(audioBytes, 0, numBytesRead);
        }

        decoder.end();
//        try {
//            decoder.getMyThread().join();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        System.out.println("END DECODE WAW FILE");
//        audioInputStream.close();
//        decoder.quit();
    }


    public static void writeWav(File file, byte[] data, AudioFormat format) throws IllegalArgumentException, IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);

        AudioInputStream ais = new AudioInputStream(bais,
                                                    format,
                                                    data.length);

        FileOutputStream outputStream = new FileOutputStream(file);
        AudioSystem.write(ais,
                          AudioFileFormat.Type.WAVE,
                          outputStream);
        outputStream.flush();
        outputStream.close();

        ais.close();
    }
    
    public static void writeWav(ByteArrayOutputStream outputStream, byte[] data, AudioFormat format) throws IllegalArgumentException, IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);

        AudioInputStream ais = new AudioInputStream(bais,
                                                    format,
                                                    data.length);

        AudioSystem.write(ais,
                          AudioFileFormat.Type.WAVE,
                          outputStream);
        outputStream.flush();
        outputStream.close();

        ais.close();
    }

    public static byte[] writeWav(byte[] data, AudioFormat format) throws IllegalArgumentException, IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);

        AudioInputStream ais = new AudioInputStream(bais,
                                                    format,
                                                    data.length);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        AudioSystem.write(ais,
                          AudioFileFormat.Type.WAVE,
                          outputStream);
        outputStream.flush();
        outputStream.close();

        ais.close();
        return outputStream.toByteArray();
    }

    public static void displayMixerInfo() {
        Mixer.Info[] mInfos = AudioSystem.getMixerInfo();
        if (mInfos == null) {
            System.out.println("No Mixers found");
            return;
        }

        for (int i = 0; i < mInfos.length; i++) {
            System.out.println("Mixer Info: " + mInfos[i]);
            Mixer mixer = AudioSystem.getMixer(mInfos[i]);
            Line.Info[] lines = mixer.getSourceLineInfo();
            for (int j = 0; j < lines.length; j++) {
                System.out.println("\tSource: " + lines[j]);
            }
            lines = mixer.getTargetLineInfo();
            for (int j = 0; j < lines.length; j++) {
                System.out.println("\tTarget: " + lines[j]);
            }
        }
    }

    public static void displayAudioFileTypes() {
        AudioFileFormat.Type[] types = AudioSystem.getAudioFileTypes();
        for (int i = 0; i < types.length; i++) {
            System.out.println("Audio File Type:" + types[i].toString());
        }
    }

    //This never returns, which is kind of lame.
    // NOT USED!! - replaced by org.codec.audio.MicrophoneListener.run()
    public static void listenToMicrophone(AudioBuffer buff) {
        try {
            int buffSize = 4096;
            TargetDataLine line = getTargetDataLine(kDefaultFormat);
            line.open(kDefaultFormat, buffSize);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int numBytesRead;
            byte[] data = new byte[line.getBufferSize() / 5];
            line.start();
            while (true) {
                numBytesRead = line.read(data, 0, data.length);
                buff.write(data, 0, numBytesRead);
            }
            /*
           line.drain();
           line.stop();
           line.close();
           */
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }

    public static void recordToFile(File file, int length) {
        try {
            int buffSize = 4096;
            TargetDataLine line = getTargetDataLine(kDefaultFormat);
            line.open(kDefaultFormat, buffSize);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int numBytesRead;
            byte[] data = new byte[line.getBufferSize() / 5];
            line.start();
            for (int i = 0; i < length; i++) {
                numBytesRead = line.read(data, 0, data.length);
                out.write(data, 0, numBytesRead);
            }
            line.drain();
            line.stop();
            line.close();

            writeWav(file, out.toByteArray(), kDefaultFormat);

        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }

    public static byte[] recordToByteArray(int length) {
        try {
            int buffSize = 4096;
            TargetDataLine line = getTargetDataLine(kDefaultFormat);
            line.open(kDefaultFormat, buffSize);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int numBytesRead;
            byte[] data = new byte[line.getBufferSize() / 5];
            line.start();
            for (int i = 0; i < length; i++) {
                numBytesRead = line.read(data, 0, data.length);
                out.write(data, 0, numBytesRead);
            }
            line.drain();
            line.stop();
            line.close();

            return out.toByteArray();
        } catch (Exception e) {
            System.out.println(e.toString());
        }
        return null;
    }

    public static TargetDataLine getTargetDataLine(AudioFormat format)
            throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException();
        }
        return (TargetDataLine) AudioSystem.getLine(info);
    }

    public static SourceDataLine getSourceDataLine(AudioFormat format)
            throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException();
        }
        return (SourceDataLine) AudioSystem.getLine(info);
    }

    public static void encodeFileToWav(File inputFile, File outputFile) throws IOException {
        FileInputStream inputStream = new FileInputStream(inputFile);

        encodeFileToWav(inputStream, outputFile);

        inputStream.close();
    }

    public static void encodeFileToWav(InputStream inputStream, File outputFile) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        AudioEncoder.encodeStream(inputStream, baos);

        inputStream.close();

        writeWav(outputFile,
                 baos.toByteArray(),
                 kDefaultFormat);
    }
    
    public static byte [] encodeFileToWav(InputStream inputStream) throws IOException {
       
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();

        AudioEncoder.encodeStream(inputStream, baos);

        inputStream.close();

        return writeWav(baos.toByteArray(),
                 kDefaultFormat);
        
    }

    public static void performData(byte[] data)
            throws IOException {
        //For some reason line.write seems to affect the data
        //to avoid the side effect, we copy it
        byte[] dataCopy = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            dataCopy[i] = data[i];
        }

        SourceDataLine line = null;
        try {
            line = getSourceDataLine(kDefaultFormat);
            line.open(kDefaultFormat);
        } catch (LineUnavailableException ex) {
            System.out.println("Line Unavailable: " + ex);
            return;
        }
        line.start();
        line.write(dataCopy, 0, dataCopy.length);
        line.drain();
        line.stop();
        line.close();
    }

    public static void performFile(File file)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        AudioEncoder.encodeStream(new FileInputStream(file), baos);
        performData(baos.toByteArray());
    }
}
