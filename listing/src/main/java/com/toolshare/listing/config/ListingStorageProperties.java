package com.toolshare.listing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "toolshare")
public class ListingStorageProperties {

    private Storage storage = new Storage();
    private Upload upload = new Upload();

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Upload getUpload() {
        return upload;
    }

    public void setUpload(Upload upload) {
        this.upload = upload;
    }

    public static class Storage {
        private Local local = new Local();

        public Local getLocal() {
            return local;
        }

        public void setLocal(Local local) {
            this.local = local;
        }
    }

    public static class Local {
        private String root = "./storage";

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }
    }

    public static class Upload {
        private DataSize maxFileSize = DataSize.ofMegabytes(5);
        private List<String> allowedMimeTypes = new ArrayList<>(List.of("image/jpeg", "image/png"));

        public DataSize getMaxFileSize() {
            return maxFileSize;
        }

        public void setMaxFileSize(DataSize maxFileSize) {
            this.maxFileSize = maxFileSize;
        }

        public List<String> getAllowedMimeTypes() {
            return allowedMimeTypes;
        }

        public void setAllowedMimeTypes(List<String> allowedMimeTypes) {
            this.allowedMimeTypes = allowedMimeTypes;
        }
    }
}