package com.erp.oa.attendance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "oa.attendance-v2")
public class AttendanceV2Properties
{
    private String storageRoot = "./uploadPath/private/attendance";
    private String tempRoot = System.getProperty("java.io.tmpdir")
            + "/erp-attendance";
    private long maxPhotoBytes = 5L * 1024L * 1024L;
    private int challengeTtlSeconds = 180;
    private int minImageWidth = 320;
    private int minImageHeight = 240;
    private int maxImagePixels = 40_000_000;
    private int maxLocationAccuracyMeters = 100;
    private Address address = new Address();

    public String getStorageRoot() { return storageRoot; }
    public void setStorageRoot(String value) { storageRoot = value; }
    public String getTempRoot() { return tempRoot; }
    public void setTempRoot(String value) { tempRoot = value; }
    public long getMaxPhotoBytes() { return maxPhotoBytes; }
    public void setMaxPhotoBytes(long value) { maxPhotoBytes = value; }
    public int getChallengeTtlSeconds() { return challengeTtlSeconds; }
    public void setChallengeTtlSeconds(int value) { challengeTtlSeconds = value; }
    public int getMinImageWidth() { return minImageWidth; }
    public void setMinImageWidth(int value) { minImageWidth = value; }
    public int getMinImageHeight() { return minImageHeight; }
    public void setMinImageHeight(int value) { minImageHeight = value; }
    public int getMaxImagePixels() { return maxImagePixels; }
    public void setMaxImagePixels(int value) { maxImagePixels = value; }
    public int getMaxLocationAccuracyMeters()
    { return maxLocationAccuracyMeters; }
    public void setMaxLocationAccuracyMeters(int value)
    { maxLocationAccuracyMeters = value; }
    public Address getAddress() { return address; }
    public void setAddress(Address value)
    { address = value == null ? new Address() : value; }

    public static class Address
    {
        private String provider = "NONE";
        private String amapWebServiceKey;
        private int connectTimeoutMillis = 1_500;
        private int readTimeoutMillis = 2_500;
        private int radiusMeters = 100;
        private int maxResponseBytes = 131_072;

        public String getProvider() { return provider; }
        public void setProvider(String value) { provider = value; }
        public String getAmapWebServiceKey() { return amapWebServiceKey; }
        public void setAmapWebServiceKey(String value)
        { amapWebServiceKey = value; }
        public int getConnectTimeoutMillis() { return connectTimeoutMillis; }
        public void setConnectTimeoutMillis(int value)
        { connectTimeoutMillis = value; }
        public int getReadTimeoutMillis() { return readTimeoutMillis; }
        public void setReadTimeoutMillis(int value)
        { readTimeoutMillis = value; }
        public int getRadiusMeters() { return radiusMeters; }
        public void setRadiusMeters(int value) { radiusMeters = value; }
        public int getMaxResponseBytes() { return maxResponseBytes; }
        public void setMaxResponseBytes(int value) { maxResponseBytes = value; }
    }
}
