package com.tencent.blazefacencnn.api.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class FaceDetectResult {
    @SerializedName("box")
    @Expose
    private List<Double> box = null;
    @SerializedName("name")
    @Expose
    private String name;
    @SerializedName("similar")
    @Expose
    private List<List<Double>> similar = null;

    public List<Double> getBox() {
        return box;
    }

    public void setBox(List<Double> box) {
        this.box = box;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<List<Double>> getSimilar() {
        return similar;
    }

    public void setSimilar(List<List<Double>> similar) {
        this.similar = similar;
    }

}