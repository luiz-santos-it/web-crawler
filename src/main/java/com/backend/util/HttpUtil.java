package com.backend.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.logging.Logger;

public class HttpUtil {
    private static final Logger LOGGER = Logger.getLogger(HttpUtil.class.getName());

    public static String getBodyTextFromUrl(URL url, int timeout) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Failed to fetch URL: " + url + " with HTTP status: " + status);
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder content = new StringBuilder();
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }

            in.close();
            return content.toString();
        } catch (SocketTimeoutException e) {
            LOGGER.warning("Timeout occurred while fetching URL: " + url + " " + e.getMessage());
            throw new IOException("Timeout while fetching URL: " + url, e);
        } catch (Exception e) {
            LOGGER.severe("Error fetching body text from URL: " + url + " " + e.getMessage());
            throw new IOException("Error fetching body text from URL: " + url, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
