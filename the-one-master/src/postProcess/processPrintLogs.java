/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package postProcess;

import java.util.*;
import java.io.*;
/**
 *
 * @author Mohammad Jahanian
 */
public class processPrintLogs {    
    public static void main(String [] args) throws FileNotFoundException, IOException{
        FileReader fr = new FileReader("C:\\Users\\mhjah\\Documents\\NetBeansProjects\\ONE\\one\\the-one-master\\reports\\myReports\\20200506\\no Interest Profle no Regioning\\"
                +  "output_logs.txt");
        BufferedReader br = new BufferedReader(fr);
        
        int createdMessagesCount = 0;
        int rcvPingCount = 0;
        int rcvResponseCount = 0;
        int rcvDuplicateCount = 0;
        double sumLatencyRel = 0;
        double sumLatencyTot = 0;
        double sumCOLatencyRel = 0;
        double sumCOLatencyTot = 0;
        double sumDecLatencyRel = 0;
        double sumDecLatencyTot = 0;
        double sumContribRound = 0;
        double totalDelivered = 0;
        double totalCompleted = 0;
        double deliveryCountRel =0;
        double deliveryCountTot =0;
        double COdeliveryCountRel =0;
        double COdeliveryCountTot =0;        
        double decisionCountRel =0;
        double decisionCountTot =0;
        double contribRoundCount = 0;
        double users = 0;
        Map <String, Double> createdMessagesTimes = new HashMap<>();
        Map <String, Double> messagesDeliveriesLatencyTot = new HashMap<>(); // key: host+msg
        Map <String, Double> messagesDeliveriesLatencyRel = new HashMap<>(); // key: host+msg
        Map <String, Double> messagesCODeliveriesLatencyTot = new HashMap<>(); // key: host+msg
        Map <String, Double> messagesCODeliveriesLatencyRel = new HashMap<>(); // key: host+msg
        
        Map<Integer, Integer> deliveredPDF = new HashMap<>();
        Map<Integer, Integer> completedPDF = new HashMap<>();
        Map<Integer, Integer> deliveredCDF = new HashMap<>();
        Map<Integer, Integer> completedCDF = new HashMap<>();
        
        Map<String, Integer> contribRounds = new HashMap<>(); //"region+slot" -> max round num
        Map<String, Double> decisionTimeRel = new HashMap<>();//"host+region+slot" -> first time
        Map<String, Double> decisionTimeTot = new HashMap<>();//"host+region+slot" -> first time
        
        Set<String> irrelevant = new HashSet<>();
        
        String line;
        while((line = br.readLine())!=null){
            if(line.contains("irrelevant")){
                String [] twoParts = line.split("\t");
                String hostName = twoParts[0].split(" ")[2];
                String mName = twoParts[1].split(" ")[0];
                irrelevant.add(hostName+"+"+mName);
            }
            if(!line.startsWith("$")){
                continue;
            }
            //System.out.println(line);
            if(line.startsWith("$CREATE")){
                createdMessagesCount++;
                String [] createElements = line.split("\t");
                String msgId = createElements[2];
                double time = Double.parseDouble(createElements[3]);
                if(!createdMessagesTimes.containsKey(msgId)){
                    createdMessagesTimes.put(msgId, time);
                }
            }
            if(line.startsWith("$RCV-P") || line.startsWith("$RCV-R")){
                if(line.startsWith("$RCV-P")){
                    rcvPingCount++;
                }
                else if(line.startsWith("$RCV-R")){
                    rcvResponseCount++;
                }
                String [] rcvElements = line.split("\t");
                String host = rcvElements[1];
                String msgId = rcvElements[2];
                double time = Double.parseDouble(rcvElements[3]);
                if(!host.startsWith("i")){
                    continue;
                }
                if(!messagesDeliveriesLatencyRel.containsKey(host+msgId) && checkUserRegion(host, msgId)){
                    messagesDeliveriesLatencyRel.put(host+msgId, (time -createdMessagesTimes.get(msgId)));
                }
                if(!messagesDeliveriesLatencyTot.containsKey(host+msgId)){
                    messagesDeliveriesLatencyTot.put(host+msgId, (time -createdMessagesTimes.get(msgId)));
                }
            }
           
            if(line.startsWith("$RCV-D")){
                rcvDuplicateCount++;
            }
            
            if(line.startsWith("$CO-DEL")){
                String [] coElements = line.split("\t");
                String host = coElements[1];
                String msgId = coElements[2];
                double time = Double.parseDouble(coElements[3]);
                if(!host.startsWith("i")){
                    continue;
                }
                if(!messagesCODeliveriesLatencyRel.containsKey(host+msgId) && checkUserRegion(host, msgId)){
                    messagesCODeliveriesLatencyRel.put(host+msgId, (time -createdMessagesTimes.get(msgId)));
                }
                if(!messagesCODeliveriesLatencyTot.containsKey(host+msgId)){
                    messagesCODeliveriesLatencyTot.put(host+msgId, (time -createdMessagesTimes.get(msgId)));
                }
            }
            
            if(line.startsWith("$CONTRIB")){
                String [] conElements = line.split("\t");
                String host = conElements[1];
                String contID = conElements[2].split(":")[0];
                String region = contID.split(",")[1];
                String slot = contID.split(",")[2];
                String round = contID.split(",")[3];
                int roundNo = Integer.parseInt(round);
                if(!contribRounds.containsKey(host+"+"+region+"+"+slot))
                    contribRounds.put(host+"+"+region+"+"+slot, roundNo);
                int latestRound = contribRounds.get(host+"+"+region+"+"+slot);
                if(latestRound < roundNo)
                    contribRounds.put(host+"+"+region+"+"+slot, roundNo);                
            }
            
            if(line.startsWith("$DECIDE-R")){
                String [] decElements = line.split("\t");
                String host = decElements[1];
                String decID = decElements[2].split(":")[0];
                String region = decID.split(",")[1];
                String slot = decID.split(",")[2];
                double time = Double.parseDouble(decElements[3]);
                if(!decisionTimeRel.containsKey(host+"+"+region+"+"+slot) && checkUserRegion(host, decID)){
                    decisionTimeRel.put((host+"+"+region+"+"+slot),((time - 3600)/3600));
                }
                if(!decisionTimeRel.containsKey(host+decID)){
                    decisionTimeRel.put((host+"+"+region+"+"+slot),((time - 3600)/3600));
                }
            }
            if(line.startsWith("$DECIDE-T")){
                String [] decElements = line.split("\t");
                String host = decElements[1];
                String decID = decElements[2].split(":")[0];
                String region = decID.split(",")[1];
                String slot = decID.split(",")[2];
                double time = Double.parseDouble(decElements[3]);
                if(!decisionTimeTot.containsKey(host+"+"+region+"+"+slot) && checkUserRegion(host, decID)){
                    decisionTimeTot.put((host+"+"+region+"+"+slot),((time - 3600)/3600));
                }
                if(!decisionTimeTot.containsKey(host+decID)){
                    decisionTimeTot.put((host+"+"+region+"+"+slot),((time - 3600)/3600));
                }
            }            
            /*
            if(line.startsWith("$FIN")){
                users++;
                String [] createElements = line.split("\t");
                int deliveredCount = Integer.parseInt(createElements[2]);
                totalDelivered += deliveredCount;
                int completedCount = Integer.parseInt(createElements[3]);
                totalCompleted += completedCount;
                //add2PDFs:delivered
                if(!deliveredPDF.containsKey(deliveredCount)){
                    deliveredPDF.put(deliveredCount, 1);
                }
                else{
                    deliveredPDF.put(deliveredCount, deliveredPDF.get(deliveredCount)+1);
                }
                if(deliveredCount > maxDeliveredCount){
                    maxDeliveredCount = deliveredCount;
                }
                //add2PDFs:completed
                if(!completedPDF.containsKey(completedCount)){
                    completedPDF.put(completedCount, 1);
                }
                else{
                    completedPDF.put(completedCount, completedPDF.get(completedCount)+1);
                }
                if(completedCount > maxCompletedCount){
                    maxCompletedCount = completedCount;
                }
            }*/
        }
        
        //fill PDFs with 0's
        for(int i=0; i<=createdMessagesCount; i++){
            if(!deliveredPDF.containsKey(i)){
                deliveredPDF.put(i, 0);
            }
            if(!completedPDF.containsKey(i)){
                completedPDF.put(i, 0);
            }
            
        }
        
        //CDF
        deliveredCDF.put(0, deliveredPDF.get(0));
        completedCDF.put(0, completedPDF.get(0));
        for(int i=1; i<=createdMessagesCount; i++){
            deliveredCDF.put(i, deliveredCDF.get(i-1)+deliveredPDF.get(i));
            completedCDF.put(i, completedCDF.get(i-1)+completedPDF.get(i));
        }
        
        System.out.println("***********");
        System.out.println("#created: "+createdMessagesCount+"\ncreation times:"+createdMessagesTimes);
        System.out.println("#rcv_p: "+rcvPingCount+" #rcv_r: "+rcvResponseCount+" #rcv_d: "+rcvDuplicateCount);
        System.out.println("#nonDupRcv: "+(rcvPingCount+rcvResponseCount-rcvDuplicateCount));
        System.out.println("#totalRelay: "+(rcvPingCount+rcvResponseCount+rcvDuplicateCount));
        System.out.println("delivery delays:");
        for(String delivery: messagesDeliveriesLatencyRel.keySet()){
            System.out.println("$DEL-Rel\t"+messagesDeliveriesLatencyRel.get(delivery));
            sumLatencyRel += messagesDeliveriesLatencyRel.get(delivery);
            deliveryCountRel++;
        }
        for(String delivery: messagesDeliveriesLatencyTot.keySet()){
            System.out.println("$DEL-Tot\t"+messagesDeliveriesLatencyTot.get(delivery));
            sumLatencyTot += messagesDeliveriesLatencyTot.get(delivery);
            deliveryCountTot++;
        }
        
        for(String delivery: messagesCODeliveriesLatencyRel.keySet()){
            System.out.println("$CODEL-Rel\t"+messagesCODeliveriesLatencyRel.get(delivery));
            sumCOLatencyRel += messagesCODeliveriesLatencyRel.get(delivery);
            COdeliveryCountRel++;
        }
        for(String delivery: messagesCODeliveriesLatencyTot.keySet()){
            System.out.println("$CODEL-Tot\t"+messagesCODeliveriesLatencyTot.get(delivery));
            sumCOLatencyTot += messagesCODeliveriesLatencyTot.get(delivery);
            COdeliveryCountTot++;
        }
        
        for(String round: contribRounds.keySet()){
            System.out.println("$CONTRIB\t"+contribRounds.get(round));
            sumContribRound += contribRounds.get(round);
            contribRoundCount++;
        }

        for(String dec: decisionTimeRel.keySet()){
            System.out.println("$DECIDE-R\t"+decisionTimeRel.get(dec));
            sumDecLatencyRel += decisionTimeRel.get(dec);
            decisionCountRel++;
        }
        for(String dec: decisionTimeTot.keySet()){
            System.out.println("$DECIDE-T\t"+decisionTimeTot.get(dec));
            sumDecLatencyTot += decisionTimeTot.get(dec);
            decisionCountTot++;
        }        
        
        System.out.print("\n");
        
        System.out.println("*****Delivered PDF CDF: ");
        for(int i=0; i<=createdMessagesCount; i++){
            System.out.println(i+"\t"+deliveredPDF.get(i)+"\t"+deliveredCDF.get(i));
        }
        System.out.print("\n");
        System.out.println("*****Completed PDF CDF: ");
        for(int i=0; i<=createdMessagesCount; i++){
            System.out.println(i+"\t"+completedPDF.get(i)+"\t"+completedCDF.get(i));
        }
        System.out.print("\n");

        System.out.println("avgLatency: Rel: "+(sumLatencyRel/deliveryCountRel)+" Tot: "+(sumLatencyTot/deliveryCountTot)+
                "\ttotalDelivery: Rel: "+deliveryCountRel);
        System.out.println("avgCOLatency: Rel: "+(sumCOLatencyRel/COdeliveryCountRel)+" Tot: "+(sumCOLatencyTot/COdeliveryCountTot)+
                "\ttotalCODelivery: Rel: "+COdeliveryCountRel);
        System.out.println("avgDecLatency: Rel: "+(sumDecLatencyRel/decisionCountRel)+" Tot: "+(sumDecLatencyTot/decisionCountTot)+
                "\ttotaldec: Rel: "+decisionCountRel);
        System.out.println("contrib rounds "+(sumContribRound/contribRoundCount)+" count: "+ contribRoundCount);
        System.out.println("avg delivered: "+(totalDelivered/users)+" %: "+(totalDelivered/(users*createdMessagesCount))+
                " avg completed: "+(totalCompleted/users)+" %: "+(totalCompleted/(users*createdMessagesCount))+
                " users: "+users);
        System.out.println("irrelevants: "+irrelevant.size());
        br.close();
        fr.close();
    }
    
    
    public static boolean checkUserRegion(String line, String msgId){
        String [] msgIdElements = msgId.split(";");
        String [] msgIdMain = msgIdElements[0].split(",");
        String region = msgIdMain[1];
        int regionInt = Integer.parseInt(region);
        if((line.contains("iV") && regionInt>=0 && regionInt <=2)
            || (line.contains("iK") && regionInt>=3 && regionInt <=7)
            || (line.contains("iU") && regionInt>=8 && regionInt <=13)
            ){
            return true;
        }
        return false;
    }
}
