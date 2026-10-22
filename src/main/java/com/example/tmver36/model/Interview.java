package com.example.tmver36.model;

public class Interview {
    private long id, teacherId;
    private String teacherName, partner, dateTime, status, result, feedback, meetingUrl;
    public Interview(long id,long teacherId,String teacherName,String partner,String dateTime,String status,String result,String feedback,String meetingUrl){
        this.id=id;this.teacherId=teacherId;this.teacherName=teacherName;this.partner=partner;this.dateTime=dateTime;this.status=status;this.result=result;this.feedback=feedback;this.meetingUrl=meetingUrl;
    }
    public long getId(){return id;} public long getTeacherId(){return teacherId;} public String getTeacherName(){return teacherName;}
    public String getPartner(){return partner;} public String getDateTime(){return dateTime;} public String getStatus(){return status;}
    public String getResult(){return result;} public String getFeedback(){return feedback;} public String getMeetingUrl(){return meetingUrl;}
    public void setStatus(String v){status=v;} public void setResult(String v){result=v;} public void setFeedback(String v){feedback=v;} public void setMeetingUrl(String v){meetingUrl=v;}
}
