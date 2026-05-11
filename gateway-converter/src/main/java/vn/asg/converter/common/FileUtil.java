/*
 */
package vn.asg.converter.common;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
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

    public static String read(String file) throws FileNotFoundException, IOException {
        
        StringBuilder sb = new StringBuilder();
        
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
            }
            // String everything = sb.toString();
        }
        
        return sb.toString();

//        try {
//
//        } finally {
//            br.close();
//        }
    }
    
    public static void main(String [] args) throws IOException {
        String content = read("reverts/METAR_VVNS_20201210000000.xml");
    }
    
    public byte[] getByteContent(String content, boolean zipped) throws UnsupportedEncodingException, IOException {
        
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

