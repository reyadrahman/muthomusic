package com.mutho.music.model;

import com.mutho.music.MuthoMusicApplication;
import com.mutho.music.interfaces.FileType;
import com.mutho.music.utils.FileHelper;
import com.mutho.music.utils.StringUtils;

public class FileObject extends BaseFileObject {

    public String extension;

    public TagInfo tagInfo;

    private long duration = 0;

    public FileObject() {
        this.fileType = FileType.FILE;
    }

    public String getTimeString() {
        if (duration == 0) {
            duration = FileHelper.getDuration(MuthoMusicApplication.getInstance(), this);
        }
        return StringUtils.makeTimeString(MuthoMusicApplication.getInstance(), duration / 1000);
    }

    @Override
    public String toString() {
        return "FileObject{" +
                "extension='" + extension + '\'' +
                ", size='" + size + '\'' +
                "} " + super.toString();
    }
}
