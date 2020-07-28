/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package applications;

import core.Message;
import java.util.*;
import java.math.*;
/**
 *
 * @author mhjah
 */
public class Namespace {
    
    String userName;
    int userAddress;
    
    //universal for incidient managers
    private static final boolean Level_0_Universal = false;
    private static final boolean Level_1_Universal = false;
    private static final boolean Level_2_Universal = false;
    
    private static final String INCIDENT = "i"; 
    
    private static final String INCIDENT_Vironniemi = "iV"; 
    private static final String INCIDENT_KampinMalmi = "iK"; 
    private static final String INCIDENT_Ullanlinna = "iU"; 
    
    private static final boolean Single_Region_Publish = false;
    
    private static final int NUM_OF_REGIONS = 14;

    private List <String> leafInterests;
        
    public Namespace(String userName, int userAddress){
        this.userName = userName;
        this.userAddress = userAddress;
        this.leafInterests = new ArrayList<>();
        if(userName.startsWith(INCIDENT_Vironniemi)){
            this.leafInterests.add("0"); //Kluuvi 
            this.leafInterests.add("1"); //Kruununhaka 
            this.leafInterests.add("2"); //Kaartinkaupunki
        }
        if(userName.startsWith(INCIDENT_KampinMalmi)){
            this.leafInterests.add("3"); //Etu-Töölö 
            this.leafInterests.add("4"); //Lapinlahti
            this.leafInterests.add("5"); //Ruoholahti 
            this.leafInterests.add("6"); //Kamppi
            this.leafInterests.add("7"); //Jätkäsaari 
        }
        if(userName.startsWith(INCIDENT_Ullanlinna)){
            this.leafInterests.add("8"); //Kaartinkaupunki
            this.leafInterests.add("9"); //Punavuori
            this.leafInterests.add("10"); //Ullanlinna 
            this.leafInterests.add("11"); //Kaivopuisto
            this.leafInterests.add("12"); //Eira
            this.leafInterests.add("13"); //Munkkisaari             
        }
    }
    
    public boolean interestedRegion(String region, int level, boolean overrideUniversality){
        if(overrideUniversality==true){//doesnt matter if universal is true
            if(this.leafInterests.contains(region)){
                return true;
            }
        }        
        else{
            if(!userName.startsWith(INCIDENT) && level!=2){
                return true;
            }
            else if(userName.startsWith(INCIDENT)){
                if(level==0 && Level_0_Universal==true){
                    return true;
                }
                if(level==1 && Level_1_Universal==true){
                    return true;
                }
                if(level==2 && Level_2_Universal==true){
                    return true;
                }
                if(this.leafInterests.contains(region)){
                    return true;
                }
            } 
        }
        return false;
    }
    
    public boolean messageMatchesInterest(Message m, int level, boolean overrideUniversality){
        String msg = m.toString();
        String [] msgElements = msg.split(",");
        if(msg.startsWith("ping") || msg.startsWith("request") || msg.startsWith("response") 
                || msg.startsWith("contribution") || msg.startsWith("decision")){
            String msgRegion = msgElements[1];
            return interestedRegion(msgRegion, level, overrideUniversality);
        }
        return false;
    }

    public String getRandomRegionToPublish(){
        if(Single_Region_Publish==true){
            return "0";
        }
        else{
            double r1 = Math.random(); //random for primary region
            int primaryRegionNo = (int)(r1 * (this.leafInterests.size()));
            String region = this.leafInterests.get(primaryRegionNo);
            System.out.print(" P_REG_NS:"+region+" ");
            return region;
        }
    }
    
    public List<String> getInterestProfile (int level){
        if(Level_2_Universal==true){
            List <String> names = new ArrayList<>();
            for(int i=0; i<NUM_OF_REGIONS; i++){
                names.add(Integer.toString(i));
            }
            return names;
        }
        else{
            return this.leafInterests;
        }
    }
    
    public String printInterestProfile (){
        String s ="L0: ";
        if(Level_0_Universal == true){
            s+="All";
        }
        else{
            s+=this.leafInterests.toString();
        }
        s+=" L1: ";
        if(Level_1_Universal == true){
            s+="All";
        }
        else{
            s+=this.leafInterests.toString();
        }
        s +=" L2: ";
        if(Level_2_Universal == true){
            s+="All";
        }
        else{
            s+=this.leafInterests.toString();
        }   
        return s;
    }
}
