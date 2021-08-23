package com.mutho.music.model;

import com.mutho.music.interfaces.FileType;

public class FolderObject extends BaseFileObject {

    public int fileCount;
    public int folderCount;

    public FolderObject() {
        this.fileType = FileType.FOLDER;
    }

    @Override
    public String toString() {
        return "FolderObject{" +
                "fileCount=" + fileCount +
                ", folderCount=" + folderCount +
                "} " + super.toString();
    }
}
