/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package applications;

import static applications.MapApplication.APP_ID;
import java.util.Random;

import core.Application;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.SimScenario;
import core.World;
import java.io.*;
import java.util.*;

import java.math.*;
/**
 *
 * @author mhjah
 */
public class Consensus {
    private String userID;
    private int userAddress;
    private Map<String, Integer> currentRound; // for each region+slot; initially 0
    private Map<String, List<Message>> currentRoundContributions; // for each region+slot
    private Map<String, Message> committed; // region+slot -> decision
    private Map<String, Boolean> roundContributed;// for each region+slot+round
    
    private DTNHost host; 
    private DTNHost randomHost;
    private int pingSize;
    
    private static double population = 10; // nbNodes; adjust
    
    public static final String FIRST_RESPONDER_PREFIX = "f";
    public static final String NAMESPACE_MANAGER_PREFIX = "i"; // or, indident managers

    Consensus(){
        this.userID = null;
        this.userAddress=-1;
        this.currentRound = new HashMap<>();
        this.currentRoundContributions = new HashMap<>();
        this.roundContributed = new HashMap<>();
        this.committed = new HashMap<>();
        this.host = null;
        this.randomHost = null;
        this.pingSize = 0;
    }
    
    public void setID (DTNHost host){
        this.host = host;
        this.userID = host.toString();
        this.userAddress = host.getAddress();
    }

    public void setRandomHost(DTNHost randomHost) {
        this.randomHost = randomHost;
    }

    public void setPingSize(int pingSize) {
        this.pingSize = pingSize;
    }
    

    public void printConsensus(){
        System.out.println(SimClock.getTime()+" consensus "+userID+" "+userAddress);
    }

    public boolean receiveContribution(Message m, Map<String, List<Message>> appliedList){
        String [] contSession = extractContributionSession(m);// 0: userID, 1: region, 2: slot, 3: round
        String region = contSession[1];
        String slot = contSession[2];
        String round = contSession[3];
        String contValue = extractContributionValue(m);
        System.out.print("\treceived contr: ");
        for(String s: contSession){
            System.out.print(s+" ");
        }
        System.out.print(" val: "+contValue+"\n");
        if(committed.containsKey(region+"+"+slot)){
            System.out.println("\tDecided; discardContribution! "+region+"+"+slot+" with: "+committed.get(region+"+"+slot));
            return false;
        }
        
        //process contribution
        if(!currentRound.containsKey(region+"+"+slot)){
            currentRound.put(region+"+"+slot, Integer.parseInt(round));
        }

        if(Integer.parseInt(round)<currentRound.get(region+"+"+slot)){
            System.out.println("\tdiscardContribution roundBehind "+m.toString());
            return false;
        }
        
        else if(Integer.parseInt(round)==currentRound.get(region+"+"+slot)){
            List <Message> contList = new ArrayList<>();
            if(currentRoundContributions.containsKey(region+"+"+slot)){
                contList = currentRoundContributions.get(region+"+"+slot);
            }
            contList.add(m);
            currentRoundContributions.put(region+"+"+slot, contList);
            if((double)(contList.size())>(0.66*population)){
                System.out.println("\t\t2/3!"+contList.size()+" "+(0.66*population)+" "+population);//+" contributions: "+currentRoundContributions);
                String fCont = findMostFrequentCont(contList);   
                Message newM = 
                        createNewContribution(region, Integer.parseInt(slot), Integer.parseInt(round)+1, fCont, true);
                host.createNewMessage(newM);
                if(!finalVal(contList).equals("noFinalVal")){
                    System.out.println("\t"+userID+" decides: "+finalVal(contList));
                    Message newD = createNewDecision(Integer.toString(userAddress), region, Integer.parseInt(slot), finalVal(contList), true);
                    host.createNewMessage(newD);
                }
            }
            else{//regular contribution, same round
                String fCont = "NoOp";
                if(appliedList.containsKey(region)){
                    List <Message> regList = appliedList.get(region);
                    if(regList.size()>Integer.parseInt(slot)){
                        fCont = regList.get(Integer.parseInt(slot)).toString();
                    }
                }
                if(!roundContributed.containsKey(region+"+"+slot+"+"+round)){
                    roundContributed.put(region+"+"+slot+"+"+round, false);
                }
                if(roundContributed.get(region+"+"+slot+"+"+round)==false){
                    Message newM = 
                                createNewContribution(region, Integer.parseInt(slot), Integer.parseInt(round), fCont, false);
                    host.createNewMessage(newM);
                }
            }
        }
        
        else if(Integer.parseInt(round)>currentRound.get(region+"+"+slot)){
            currentRound.put(region+"+"+slot, Integer.parseInt(round));
            //clean current round contributions
            List <Message> oldContList = currentRoundContributions.get(region+"+"+slot);
            for(Message oc: oldContList){
                if(host.getRouter().hasMessage(oc.getId())){
                    host.deleteMessage(oc.getId(), false);
                    System.out.println("\t"+host+" oldRoundContrib "+oc.toString()+" removed from buffer: "+oc.toString());
                }    
            }
            List <Message> contList = new ArrayList<>();
            contList.add(m);
            currentRoundContributions.put(region+"+"+slot, contList);
            //System.out.println("\tcontributions "+currentRoundContributions+ " round: "+round);
            String fCont = "NoOp";
            if(appliedList.containsKey(region)){
                List <Message> regList = appliedList.get(region);
                if(regList.size()>Integer.parseInt(slot)){
                    fCont = regList.get(Integer.parseInt(slot)).toString();
                }
            } 
            if(!roundContributed.containsKey(region+"+"+slot+"+"+round)){
                roundContributed.put(region+"+"+slot+"+"+round, false);
            }
            if(roundContributed.get(region+"+"+slot+"+"+round)==false){
                Message newM = createNewContribution(region, Integer.parseInt(slot), Integer.parseInt(round), fCont, false);
                host.createNewMessage(newM);
            }
        }
        
        //System.out.println("\tcontributions "+currentRoundContributions+ " round: "+currentRound.get(region+"+"+Integer.parseInt(slot)));
        return true;
    }


    public boolean receiveDecision(Message m){
        String [] contSession = extractDecisionSession(m);// 0:src  1: region, 2: slot
        String srcID = contSession[0];
        String region = contSession[1];
        String slot = contSession[2];
        String decValue = extractDecisionValue(m);
        System.out.print("received dec: ");
        for(String s: contSession){
            System.out.print(s+" ");
        }
        System.out.print(" val: "+decValue+"\n");
        if(committed.containsKey(region+"+"+slot)){
            Message oldDec = committed.get(region+"+"+slot);
            String [] OcontSession = extractDecisionSession(oldDec);// 0:src  1: region, 2: slot
            String OsrcID = OcontSession[0];
            String OdecValue = extractDecisionValue(oldDec);
            if(OsrcID.equals(srcID) && OdecValue.equals(decValue)){
                System.out.println("\tDecided;discard sameSrcSameVal! "+region+"+"+slot+" with: "+committed.get(region+"+"+slot));
                return false;
            }
            else if(!OsrcID.equals(srcID) && OdecValue.equals(decValue)){
                System.out.println("\tDecided diffSrcSameVal! "+region+"+"+slot+" with: "+committed.get(region+"+"+slot)+
                " newSrc: "+srcID+" oldSrc: "+OsrcID);
                if(Integer.parseInt(srcID)>Integer.parseInt(OsrcID)){
                    host.deleteMessage(oldDec.getId(), false);
                    System.out.println("\t"+host+" updated dec "+m.toString()+" removed from buffer: "+oldDec.toString());
                    Message newD = createNewDecision(srcID, region, Integer.parseInt(slot), decValue, false);
                    host.createNewMessage(newD);
                }
                else if (Integer.parseInt(srcID)<=Integer.parseInt(OsrcID)){
                    //host.deleteMessage(m.getId(), false);
                    System.out.println("\t"+host+" Notupdated dec "+oldDec.toString()+" newDec discarded: "+m.toString());
                    return false;
                }
                return true;
            }
        }
        //System.out.println("\t"+userID+" decides: "+decValue);
        Message newD = createNewDecision(srcID, region, Integer.parseInt(slot), decValue, true);
        host.createNewMessage(newD);
        return true;
    }
    
    
    public String [] extractContributionSession (Message m){
        String cont = m.toString().replace("contribution-", "");
        String [] contElements = cont.split(":"); 
        String [] contElements1 = contElements[0].split(",");// 0: userID, 1: region, 2: slot, 3: round
        return contElements1;
    }
    
    public String extractContributionValue (Message m){
        String cont = m.toString().replace("contribution-", "");
        String [] contElements = cont.split(":"); 
        return contElements[1];
    }
    
    public String [] extractDecisionSession (Message m){
        String cont = m.toString().replace("decision-", "");
        String [] contElements = cont.split(":"); 
        String [] contElements1 = contElements[0].split(",");// 0: src, 1: region, 2: slot
        return contElements1;
    }
    
    public String extractDecisionValue (Message m){
        String cont = m.toString().replace("decision-", "");
        String [] contElements = cont.split(":"); 
        return contElements[1];
    }
    
    public String findMostFrequentCont(List<Message> contList){
        Map <String, Integer> valueCounts = new HashMap<>();
        for(Message c: contList){
            String cVal = extractContributionValue(c);
            if(valueCounts.containsKey(cVal)){
                valueCounts.put(cVal, valueCounts.get(cVal)+1);
            }
            else{
                valueCounts.put(cVal, 1);
            }
        }
        int maxFreq = 0;
        String maxVal = "NoOp";
        for(String val:valueCounts.keySet()){
            if(val.equals("NoOp")){
                continue;
            }
            if(valueCounts.get(val)>maxFreq){
                maxFreq = valueCounts.get(val);
                maxVal = val;
            }
            else if(valueCounts.get(val)==maxFreq){
                if(val.compareTo(maxVal)<0){
                    maxVal = val;
                }
            }
        }
        return maxVal;
    }
    
    public String finalVal(List<Message> contList){// if maxVal has more 2/3 count
        Map <String, Integer> valueCounts = new HashMap<>();
        for(Message c: contList){
            String cVal = extractContributionValue(c);
            if(valueCounts.containsKey(cVal)){
                valueCounts.put(cVal, valueCounts.get(cVal)+1);
            }
            else{
                valueCounts.put(cVal, 1);
            }
        }
        int maxFreq = 0;
        String maxVal = "";
        for(String val:valueCounts.keySet()){
            if(val.equals("NoOp")){
                continue;
            }
            if(valueCounts.get(val)>maxFreq){
                maxFreq = valueCounts.get(val);
                maxVal = val;
            }
            else if(valueCounts.get(val)==maxFreq){
                if(val.compareTo(maxVal)<0){
                    maxVal = val;
                }
            }
        }
        if(maxFreq > ((0.66)*population))
            return maxVal;
        else
            return "noFinalVal";
    }

    public Message createNewContribution(String region, int slot, int round, String value, boolean newRound){
        value = value.replace("response", "ping");
        currentRound.put(region+"+"+Integer.toString(slot), round);
        String msgID = "contribution-"+host.getAddress()+","+region+","+slot+","+round+":"+value;       
        Message m = new Message(host, randomHost, msgID ,pingSize);
        System.out.println("\tnew contribution: "+m.toString()+" round: "+currentRound.get(region+"+"+Integer.toString(slot)));
        m.addProperty("type", "contribution");
        m.setAppID(APP_ID);
        List <Message> contList = new ArrayList<>();
        if(newRound==true){
            contList.add(m);
            //delete old contributions from buffer
            if(currentRoundContributions.containsKey(region+"+"+slot)){
                List<Message> oldContList = currentRoundContributions.get(region+"+"+slot);
                for(Message ocl: oldContList){
                    if(host.getRouter().hasMessage(ocl.getId())){
                        host.deleteMessage(ocl.getId(), false);
                        System.out.println("\t"+host+" made contr "+m.toString()+" removed from buffer: "+ocl.toString());
                    }
                }
            }
            //
            currentRoundContributions.put(region+"+"+slot, contList);
        }
        else{//same round
            if(currentRoundContributions.containsKey(region+"+"+slot)){
                contList = currentRoundContributions.get(region+"+"+slot);
                //System.out.println("same round1, "+currentRoundContributions.get(region+"+"+slot));
            }
            contList.add(m);
            currentRoundContributions.put(region+"+"+slot, contList);
            //System.out.println("same round, "+contList);
        }
        roundContributed.put(region+"+"+slot+"+"+round, true);
        System.out.println("$CONTRIB\t"+host+"\t"+m.toString().replace("contribution", "")+"\t"+SimClock.getTime());        
        return m;
    }
    
    public Message createNewDecision(String srcAddress, String region, int slot, String value, boolean original){
        String msgID = "decision-"+srcAddress+","+region+","+slot+":"+value;       
        Message m = new Message(host, randomHost, msgID ,pingSize);
        System.out.println("\t"+userAddress+" new decision: "+m.toString()+" round: "+currentRound.get(region+"+"+Integer.toString(slot)));
        m.addProperty("type", "decision");
        m.setAppID(APP_ID);
        committed.put(region+"+"+Integer.toString(slot), m);
        host.completed++;
        if(host.getNs().messageMatchesInterest(m, 2, true)){
            host.completedInterested++;
        }
        //clean contributions of this
        List <Message> oldContList = currentRoundContributions.get(region+"+"+slot);
        for(Message oc: oldContList){
            if(host.getRouter().hasMessage(oc.getId())){
                host.deleteMessage(oc.getId(), false);
                System.out.println("\t"+host+" notNeededContrib "+oc.toString()+" removed from buffer: "+oc.toString());
            }    
        }       
        if(original){
            if(host.getNs().messageMatchesInterest(m, 2, true)){
                System.out.println("$DECIDE-R-O\t"+host+"\t"+m.toString().replace("decision", "")+"\t"+SimClock.getTime());
            }
            System.out.println("$DECIDE-T-O\t"+host+"\t"+m.toString().replace("decision", "")+"\t"+SimClock.getTime());
        }
        else{
            if(host.getNs().messageMatchesInterest(m, 2, true)){
                System.out.println("$DECIDE-R-U\t"+host+"\t"+m.toString().replace("decision", "")+"\t"+SimClock.getTime());
            }
            System.out.println("$DECIDE-T-U\t"+host+"\t"+m.toString().replace("decision", "")+"\t"+SimClock.getTime());
        }        
        return m;
    }
    
    public int getNumberOfCommitted(){
        return committed.size();
    }
        
    public int getNumberOfCommittedInterested(){
        int c=0;
        for(String s: committed.keySet()){
            String [] sElems =  s.split("\\+");
            String sReg = sElems[0];
            if(host.getNs().interestedRegion(sReg, 2, true)){
                if(committed.get(s)!=null){
                    c++;
                }
            }
        }
        return c;
    }
}
