/*
 */
package vn.asg.converter.utils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.zip.GZIPOutputStream;

/**
 *
 * @author ThanhNk
 */
public class FileUtil {

    public static String readFile(String filePath) throws FileNotFoundException, IOException {
        String st;
        StringBuilder builder = new StringBuilder();
        String lineBreak = "";

        try (BufferedReader br = new BufferedReader(new FileReader(new File(filePath)))) {

            while ((st = br.readLine()) != null) {
                builder.append(lineBreak);
                builder.append(st);
                lineBreak = "\n";
            }
        }

        return builder.toString();
    }
    
    public static String readFile(File filePath) throws FileNotFoundException, IOException {
        String st;
        StringBuilder builder = new StringBuilder();
        String lineBreak = "";

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {

            while ((st = br.readLine()) != null) {
                builder.append(lineBreak);
                builder.append(st);
                lineBreak = "\n";
            }
        }

        return builder.toString();
    }
    
    
    public static byte[] readFile(String filePath, boolean zipped) throws FileNotFoundException, IOException {
        String st;
        StringBuilder builder = new StringBuilder();
        String lineBreak = "";

        try (BufferedReader br = new BufferedReader(new FileReader(new File(filePath)))) {

            while ((st = br.readLine()) != null) {
                builder.append(lineBreak);
                builder.append(st);
                lineBreak = "\n";
            }
        }

        byte [] data = builder.toString().getBytes();
        
        if (!zipped) {
            return data;
        }
        
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
            try (GZIPOutputStream zipStream = new GZIPOutputStream(byteStream)) {
                zipStream.write(data);
                return byteStream.toByteArray();
            }
        }
    }
    
    public static byte[] readFile(File file, boolean zipped) throws FileNotFoundException, IOException {
        String st;
        StringBuilder builder = new StringBuilder();
        String lineBreak = "";

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {

            while ((st = br.readLine()) != null) {
                builder.append(lineBreak);
                builder.append(st);
                lineBreak = "\n";
            }
        }

        byte [] data = builder.toString().getBytes();
        
        if (!zipped) {
            return data;
        }
        
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
            try (GZIPOutputStream zipStream = new GZIPOutputStream(byteStream)) {
                zipStream.write(data);
                return byteStream.toByteArray();
            }
        }
    }
    
    public static byte[] getByteContent(String content, boolean zipped) throws UnsupportedEncodingException, IOException {
        
        if (content == null) {
            return new byte[0];
        }
        
        if (!zipped) {
            return content.getBytes("UTF-8");
        }
        
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            try (GZIPOutputStream gzipStream = new GZIPOutputStream(outputStream)) {
                gzipStream.write(content.getBytes("UTF-8"));
                gzipStream.close();
            }
            outputStream.close();
            return outputStream.toByteArray();
        }
    }
}

