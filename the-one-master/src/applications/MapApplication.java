/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
//NameApplication + consensus + namespace
package applications;

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
 * Simple ping application to demonstrate the application support. The
 * application can be configured to send pings with a fixed interval or to only
 * answer to pings it receives. When the application receives a ping it sends
 * a pong message in response.
 *
 * The corresponding <code>PingAppReporter</code> class can be used to record
 * information about the application behavior.
 *
 * @see PingAppReporter
 * @author teemuk
 */
public class MapApplication extends Application {
	/** Run in passive mode - don't generate pings but respond */
	public static final String PING_PASSIVE = "passive";
	/** Ping generation interval */
	public static final String PING_INTERVAL = "interval";
	/** Ping interval offset - avoids synchronization of ping sending */
	public static final String PING_OFFSET = "offset";
	/** Destination address range - inclusive lower, exclusive upper */
	public static final String PING_DEST_RANGE = "destinationRange";
	/** Seed for the app's random number generator */
	public static final String PING_SEED = "seed";
	/** Size of the ping message */
	public static final String PING_PING_SIZE = "pingSize";
	/** Size of the pong message */
	public static final String PING_PONG_SIZE = "pongSize";
        
        public static final String FIRST_RESPONDER_PREFIX = "f";
        public static final String NAMESPACE_MANAGER_PREFIX = "i"; // or, indident managers

        public static final boolean Causal_Ordering = true; //adjust
        public static final boolean REACTIVE = true; // adjust
        public static final boolean ConsesusEnabled = true; //adjust
        
        public static final int endSimTime = 43200; // adjust
        public static final int endSendTime = 1801; // adjust
        public static final int consensusTime = 3600; // adjust
        
        public static final int NUM_OF_REGIONS = 3; // adjust; start with 0
        public static final double twoRegionProb = 0; // adjust
        
	/** Application ID */
	public static final String APP_ID = "fi.tkk.netlab.PingApplication";
        
        public Set <Message> appliedMessageSet; // applied/accepted msgs only
        public Map <String, List<Message>> appliedMessagesLists; // applied msgs list; region -> list 
        public Map <String, List<Message>> allMessages; //all messages indexed by sender id + region

        //3 copies for app view
        public Set <Message> appliedMessageSetInterested; // applied/accepted msgs only
        public Map <String, List<Message>> appliedMessagesListsInterested; // applied msgs list; region -> list 
        public Map <String, List<Message>> allMessagesInterested; //all messages indexed by sender id + region

        
        public Map <String, List<String>> references; // value: references of key
        public Map <String, List<String>> referenceFor; // key: a refernce for value
        
        public Map <Integer, Integer> createdSeq; // key: RegionID, value: lastSentSeqNum

        public Consensus consensus;
        
	// Private vars
	private double	lastPing = 0;
	private double	interval = 500;
	private boolean passive = false;
	private int		seed = 0;
	private int		destMin=0;
	private int		destMax=1;
	private int		pingSize=1;
	private int		pongSize=1;
	private Random	rng;

	/**
	 * Creates a new ping application with the given settings.
	 *
	 * @param s	Settings to use for initializing the application.
	 */
	public MapApplication(Settings s) {
		if (s.contains(PING_PASSIVE)){
			this.passive = s.getBoolean(PING_PASSIVE);
		}
		if (s.contains(PING_INTERVAL)){
			this.interval = s.getDouble(PING_INTERVAL);
		}
		if (s.contains(PING_OFFSET)){
			this.lastPing = s.getDouble(PING_OFFSET);
		}
		if (s.contains(PING_SEED)){
			this.seed = s.getInt(PING_SEED);
		}
		if (s.contains(PING_PING_SIZE)) {
			this.pingSize = s.getInt(PING_PING_SIZE);
		}
		if (s.contains(PING_PONG_SIZE)) {
			this.pongSize = s.getInt(PING_PONG_SIZE);
		}
		if (s.contains(PING_DEST_RANGE)){
			int[] destination = s.getCsvInts(PING_DEST_RANGE,2);
			this.destMin = destination[0];
			this.destMax = destination[1];
		}
                
                appliedMessageSet = new HashSet<> ();
                allMessages = new HashMap<>();
                appliedMessagesLists = new HashMap<>();
                
                consensus = new Consensus();
                
		rng = new Random(this.seed);
		super.setAppID(APP_ID);
	}

	/**
	 * Copy-constructor
	 *
	 * @param a
	 */
	public MapApplication(MapApplication a) {
		super(a);
		this.lastPing = a.getLastPing();
		this.interval = a.getInterval();
		this.passive = a.isPassive();
		this.destMax = a.getDestMax();
		this.destMin = a.getDestMin();
		this.seed = a.getSeed();
		this.pongSize = a.getPongSize();
		this.pingSize = a.getPingSize();
		this.rng = new Random(this.seed);
                this.appliedMessageSet = new HashSet<> ();
                this.allMessages = new HashMap<>();
                this.appliedMessagesLists = new HashMap<>();
                this.appliedMessageSetInterested = new HashSet<> ();
                this.allMessagesInterested = new HashMap<>();
                this.appliedMessagesListsInterested = new HashMap<>();
                this.references = new HashMap<>();
                this.referenceFor = new HashMap<>();
                this.createdSeq = new HashMap<>();
                this.consensus = new Consensus();
	}

	/**
	 * Handles an incoming message. If the message is a ping message replies
	 * with a pong message. Generates events for ping and pong messages.
	 *
	 * @param msg	message received by the router
	 * @param host	host to which the application instance is attached
	 */
	@Override
	public Message handle(Message msg, DTNHost host) {
                System.out.println(SimClock.getTime()+" Host: "+ host.toString()+" Received: "+msg.toString()+" ");
		String type = (String)msg.getProperty("type");
		if (type==null) return msg; // Not a ping/pong message
                String [] msgOrigins = getOriginAddressFromText(msg);
                System.out.print(" creationTime: "+msg.getCreationTime()+
                        " orign(user region seq): "+msgOrigins[0]+ " "+ msgOrigins[1]+" "+ msgOrigins[2]+
                        " Refs: "+ extractRefsFromText(msg)+
                        " to:  "+msg.getTo()+"\n");
                
                if(type.equalsIgnoreCase("ping")){
                    addNewUpdate(msg, host);
                    System.out.println("$RCV-P\t"+host+"\t"+msg.toString().replace("ping", "")+"\t"+SimClock.getTime());
                }
                
                //if request
                if(type.equalsIgnoreCase("request")){
                    String req_str = msg.toString();
                    req_str=req_str.replace("request", "");
                    String [] req_elements = req_str.split("-");
                    String [] reqOrigins = getOriginAddressFromText(msg);
                    String req_origin = reqOrigins[0]+","+reqOrigins[1]; // user+region
                    int req_seqNum = Integer.parseInt(reqOrigins[2]); // seqNum
                    System.out.println("\treqRecvFor: "+msg.toString()+" creation time: "+msg.getCreationTime()+ 
                            " origins: "+ req_origin+ " seqNum: "+req_seqNum);
                    if(!allMessages.containsKey(req_origin)){
                        System.out.println("\t\tDon't have anything from"+req_origin);
                    }
                    else{
                        //have from origin(id+regino), check for seqNum
                        List<Message> mList = allMessages.get(req_origin);
                        int flag4=0;
                        for(Message m4: mList){
                            System.out.println("\t\tmList4ReqCheck: "+mList);
                            flag4=0;
                            if(getSeqNumFromText(m4)==req_seqNum){
                                flag4=1;
                                System.out.println("\t\tYes, Have "+m4);
                                //send response
                                
                                //Message m5 = m4.replicate();
                                
                                String id5 = "response-" +
                                        getOriginAddressFromText(m4)[0]+","+getOriginAddressFromText(m4)[1]+
                                        ","+req_seqNum;
                                //add refs
                                //System.out.println("\t\tRefsForResponse: "+references.get(getOriginAddressFromText(m4)[0]+","+getOriginAddressFromText(m4)[1]+
                                //        ","+req_seqNum));
                                for (String m5Refs: references.get(getOriginAddressFromText(m4)[0]+","+getOriginAddressFromText(m4)[1]+
                                        ","+req_seqNum)){
                                    id5=id5+";-"+m5Refs;
                                }
                                Message m5 = new Message(host, msg.getFrom(), id5, getPongSize());
                                m5.addProperty("type", "response");
                                m5.setAppID(APP_ID);
                                host.createNewMessage(m5);
                                System.out.println("\t\t\t send "+m5.toString()+" to "+msg.getFrom());
                                // Send event to listeners
                                super.sendEventToListeners("GotPing", null, host);
                                super.sendEventToListeners("SentPong", null, host);
                                break;
                            }
                        }
                    }
                }
                
                //if response to request received (chocie: only sent to me, or sent to anyone)
//		if (msg.getTo()==host && type.equalsIgnoreCase("response")) {
//                        System.out.println("\t***This is sent to me!");
		if (type.equalsIgnoreCase("response")) {
                        System.out.println("\t***This is sent to me or anyone!");
                        addNewUpdate(msg, host);
                        System.out.println("$RCV-R\t"+host+"\t"+msg.toString().replace("response", "")+"\t"+SimClock.getTime());

			// Send event to listeners
			super.sendEventToListeners("GotPing", null, host);
			//super.sendEventToListeners("SentPong", null, host);
		}
                
                if(type.equalsIgnoreCase("contribution") && host.toString().startsWith(NAMESPACE_MANAGER_PREFIX)){    
                    boolean b = consensus.receiveContribution(msg, appliedMessagesLists);
                    if(b==false){
                        return null;
                    }
                }

                if(type.equalsIgnoreCase("decision") && host.toString().startsWith(NAMESPACE_MANAGER_PREFIX)){    
                    boolean b = consensus.receiveDecision(msg);
                    if(b==false){
                        return null;
                    }
                }
                
		// Received a pong reply
		if (msg.getTo()==host && type.equalsIgnoreCase("pong")) {
			// Send event to listeners
			super.sendEventToListeners("GotPong", null, host);
		}

		return msg;
	}

	/**
	 * Draws a random host from the destination range
	 *
	 * @return host
	 */
	private DTNHost randomHost() {
		int destaddr = 0;
		if (destMax == destMin) {
			destaddr = destMin;
		}
		destaddr = destMin + rng.nextInt(destMax - destMin);
		World w = SimScenario.getInstance().getWorld();
		return w.getNodeByAddress(destaddr);
	}

	@Override
	public Application replicate() {
		return new MapApplication(this);
	}

	/**
	 * Sends a ping packet if this is an active application instance.
	 *
	 * @param host to which the application instance is attached
	 */
	@Override
	public void update(DTNHost host) {
		if (this.passive) return;
		double curTime = SimClock.getTime();
//                double locX = host.getLocation().getX();
//                double locY = host.getLocation().getY();
//                if(locX>400 && locY>150){
//                    System.out.println(host.toString()+" Issubscribed");
//                }
//                else{
//                    System.out.println(host.toString()+" Unsubscribed");
//                }
                if(curTime == 1){// initialize consensu
                    consensus.setID(host);
                    consensus.setRandomHost(randomHost());
                    consensus.setPingSize(getPingSize());
                    System.out.println(host.toString()+" interests: "+host.getNs().printInterestProfile());
                }
               
                if(//!host.toString().startsWith(FIRST_RESPONDER_PREFIX) && 
                        !host.toString().startsWith(NAMESPACE_MANAGER_PREFIX)){
                    return;
                }
                
                if(curTime == (endSimTime)){// print final logs
                    System.out.println("$FIN\t"+host+"\t"+
                            mapSize(allMessages)+"\t("+countInterestedLevel0(host)+")\t"+
                            appliedMessageSet.size()+"\t("+countInterestedLevel1(host)+")\t"+
                            consensus.getNumberOfCommitted()+"\t("+consensus.getNumberOfCommittedInterested()+")\t"+
                            host.getBufferOccupancy());
                }
                
		if (curTime - this.lastPing >= this.interval && curTime <= endSendTime) {
			// Time to send a new ping
                        //ping-10,1,0+REF ("ping"_user,Region,Seq)
                        System.out.print(SimClock.getTime()+" "+host.toString()+" ");
                        Message m = createNewPing(host);
			host.createNewMessage(m);

                        if(host.toString().startsWith(NAMESPACE_MANAGER_PREFIX)){//add my own
                            System.out.print("\t\tcreated "+ m.toString()+" loc: "+host.getLocation()+ "\n");
                            System.out.println("$CREATE\t"+host+"\t"+m.toString().replace("ping", "")+"\t"+curTime);
                            addNewUpdate(m, host);
                        }
                                
                        
                        // Call listeners
			super.sendEventToListeners("SentPing", null, host);

			this.lastPing = curTime;
		}
                if(curTime == consensusTime && ConsesusEnabled){
                    consensus.printConsensus();
                    System.out.println("$MID\t"+host+"\t"+
                            mapSize(allMessages)+"\t("+countInterestedLevel0(host)+")\t"+
                            appliedMessageSet.size()+"\t("+countInterestedLevel1(host)+")\t"+
                            consensus.getNumberOfCommitted()+"\t("+consensus.getNumberOfCommittedInterested()+")\t"+
                            host.getBufferOccupancy());
                    for(String rS: host.getNs().getInterestProfile(2)){
                        int r = Integer.parseInt(rS);
                        if(appliedMessagesLists.containsKey(Integer.toString(r))){
                            List <Message> regList = appliedMessagesLists.get(Integer.toString(r));
                            for(int rl=0; rl<regList.size(); rl++){
                                String value00 = regList.get(rl).toString();
                                Message m = consensus.createNewContribution(Integer.toString(r), rl, 0, value00, true);
                                host.createNewMessage(m);
                                System.out.println("$INIT-CON\t"+host+"\t"+m.toString().replace("contribution", "")+"\t"+SimClock.getTime());
                            }
                        }
                    }
                }
	}

	/**
	 * @return the lastPing
	 */
	public double getLastPing() {
		return lastPing;
	}

	/**
	 * @param lastPing the lastPing to set
	 */
	public void setLastPing(double lastPing) {
		this.lastPing = lastPing;
	}

	/**
	 * @return the interval
	 */
	public double getInterval() {
		return interval;
	}

	/**
	 * @param interval the interval to set
	 */
	public void setInterval(double interval) {
		this.interval = interval;
	}

	/**
	 * @return the passive
	 */
	public boolean isPassive() {
		return passive;
	}

	/**
	 * @param passive the passive to set
	 */
	public void setPassive(boolean passive) {
		this.passive = passive;
	}

	/**
	 * @return the destMin
	 */
	public int getDestMin() {
		return destMin;
	}

	/**
	 * @param destMin the destMin to set
	 */
	public void setDestMin(int destMin) {
		this.destMin = destMin;
	}

	/**
	 * @return the destMax
	 */
	public int getDestMax() {
		return destMax;
	}

	/**
	 * @param destMax the destMax to set
	 */
	public void setDestMax(int destMax) {
		this.destMax = destMax;
	}

	/**
	 * @return the seed
	 */
	public int getSeed() {
		return seed;
	}

	/**
	 * @param seed the seed to set
	 */
	public void setSeed(int seed) {
		this.seed = seed;
	}

	/**
	 * @return the pongSize
	 */
	public int getPongSize() {
		return pongSize;
	}

	/**
	 * @param pongSize the pongSize to set
	 */
	public void setPongSize(int pongSize) {
		this.pongSize = pongSize;
	}

	/**
	 * @return the pingSize
	 */
	public int getPingSize() {
		return pingSize;
	}

	/**
	 * @param pingSize the pingSize to set
	 */
	public void setPingSize(int pingSize) {
		this.pingSize = pingSize;
	}
        
        
        public void addNewUpdate(Message m, DTNHost host){// m received; host: me
            // first check in and add to map
            //DTNHost from = m.getFrom();
            String from_str = getOriginAddressFromText(m)[0];
            String region_str = getOriginAddressFromText(m)[1];
            String seqNum_str = getOriginAddressFromText(m)[2];
            List <String> holes = new ArrayList<>();
            if(!allMessages.containsKey(from_str+","+region_str)){
                List<Message> mList = new ArrayList<>();
                allMessages.put(from_str+","+region_str, mList);
            }
            List<Message> mList = allMessages.get(from_str+","+region_str);
            //check response and ping duplicates
            for(Message mZ: mList){
                String [] mZ_orig = getOriginAddressFromText(mZ);
                if(mZ_orig[0].equals(from_str) && mZ_orig[1].equals(region_str) && mZ_orig[2].equals(seqNum_str)){
//                if(mZ.getId().equals(m.getId().replace("response", "ping")) || 
//                        mZ.getId().equals(m.getId().replace("ping", "replace")) || mZ.getId().equals(m.getId()) ){
                    System.out.println("\tDuplicate!"+m.toString()+" & "+mZ.toString()+" ... Nothing to Do!");
                    System.out.println("$RCV-D\t"+host+"\t"+m.toString().replace("ping", "").replace("response", "")+"\t"+SimClock.getTime());
                    return;
                }
            }
            mList.add(m); 
            allMessages.put(from_str+","+region_str, mList);
            if(Causal_Ordering==false){
                List<Message> regionMsgList = new ArrayList<>();
                if(appliedMessagesLists.containsKey(region_str)){
                    regionMsgList = appliedMessagesLists.get(region_str);
                }
                regionMsgList.add(m);
                appliedMessagesLists.put(region_str, regionMsgList);
                appliedMessageSet.add(m);
            }
            //Update dependency list
            updateReferencesMaps(m);
            if(Causal_Ordering==true){
                addToAppliedMessageSet(mList, host);
            }
            

            
            if(Causal_Ordering==true && REACTIVE==true){
                //find holes and add
                //implicit holes add (start from 1 up to seqNum of this update; not max
                //int maxSeqNum = findMaxSeqNumInMap(from_str+","+region_str);
                holes = findAndAddToHoles (from_str, region_str, holes, mList, Integer.parseInt(seqNum_str));
                
                //explciit holes: process dependencies
                for(String ref: references.get(from_str+","+region_str+","+seqNum_str)){
                    System.out.print("\t\tExplicitHole: "+ref);
                    String [] refElements = ref.split(",");
                    List<Message> refList = allMessages.get(refElements[0]+","+refElements[1]);
                    //System.out.println("\t\tRefmList: "+refList);
                    holes = findAndAddToHoles (refElements[0], refElements[1], holes, refList, Integer.parseInt(refElements[2]));
                }
                System.out.println();

                //send request for each hole
                for(String h: holes){
                    String id = "request" + h;
                    Message mh = new Message(host, randomHost() , id, getPongSize());
                    mh.addProperty("type", "request");
                    mh.setAppID(APP_ID);
                    host.createNewMessage(mh);
                    System.out.println("\t\tsendReq "+mh.toString());
//
                    //Send event to listeners
                    super.sendEventToListeners("GotPing", null, host);
                    super.sendEventToListeners("SentPong", null, host);
                }
            }
        
            //print result of updates
            System.out.println("\tsizes:");
            System.out.println("\t"+mapSize(allMessages));//+"\t"+allMessages);
            System.out.println("\t"+appliedMessageSet.size());//+"\t"+appliedMessageSet);
            System.out.println("\t"+mapSize(appliedMessagesLists));//+"\t"+appliedMessagesLists);
            System.out.println("\t"+holes.size());//+"\t"+holes);

        }
       
        public int mapSize(Map <String, List<Message>> msgMap){
            int count=0;
            for(String s: msgMap.keySet()){
                count+=msgMap.get(s).size();
            }
            return count;
        }
        
        public double findMaxInMap(String from){//max creation time for an origin
            double max = 0;
            List <Message> mList = new ArrayList<>();
            mList = allMessages.get(from);
            for(Message m: mList){
                double thisC = getCreationTimeFromText(m);
                if(thisC> max){
                    max= thisC;
                }
            }
            return max;
        }
        
        public String [] getOriginAddressFromText(Message m){// 0: userID 1: Region 2: seqNum
            String text = m.toString();
            String [] allElements = text.split(";");
            String [] elements = allElements[0].split("-");
            String [] originElements = elements[elements.length-1].split(",");
            return originElements;
        }

        public double getCreationTimeFromText(Message m){
            String text = m.toString();
            if(text.startsWith("request"))
                text=text.replace("request", "");
            if(text.startsWith("ping"))
                text=text.replace("ping", "");
            if(text.startsWith("response"))
                text=text.replace("response", "");
            String [] elements = text.split("-");
            return Double.parseDouble(elements[0]);
        }
        
        public int getSeqNumFromText(Message m){
            return Integer.parseInt(getOriginAddressFromText(m)[2]);
        }
        
        public int findMaxSeqNumInMap(String from){//max seq num for an origin(user+Region)
            int max = 0;
            List <Message> mList = new ArrayList<>();
            mList = allMessages.get(from);
            for(Message m: mList){
                int thisC = getSeqNumFromText(m);
                if(thisC> max){
                    max= thisC;
                }
            }
            return max;
        }
        
        public void addToAppliedMessageSet(List<Message> mList, DTNHost host){
            int index=1;
            int flag=0;
            int flagExp=0;
            while(true){
                flag=0;
                flagExp=0;
                for(Message m1: mList){
                    if(getSeqNumFromText(m1)==index){
                            List<String> m1Refs = references.get(getOriginAddressFromText(m1)[0]+","+getOriginAddressFromText(m1)[1]+
                                    ","+getOriginAddressFromText(m1)[2]);
                            boolean allRefsApplied = true;
                            for(String m1Ref: m1Refs){
                                if(!appliedContains(m1Ref)){
                                    allRefsApplied = false;
                                    break;
                                }
                            }
                            flag=1;
                            //if m1 has no pending explicit dependencies (if all references of m1 are already in appliedMessages)
                            if(allRefsApplied == true){
                                appliedMessageSet.add(m1);
                                applyToMessageList(m1);
                                if(host.toString().startsWith("i")){
                                    System.out.println("$CO-DELIVER\t"+host+"\t"+m1.toString().replace("ping", "").replace("response", "")+"\t"+SimClock.getTime());
                                }
                                //apply all pending messages depending on m1
                                //& for any pending dependant's mList; run addToAppliedMessageSet (to add implicit dependencies of those as well)
                                List <String> m1RefForS = referenceFor.get(m1);
                                if(m1RefForS!=null){
                                    for(String m1RefFor: m1RefForS){
                                        String [] m1RefForElements = m1RefFor.split(",");
                                        String m11_orig = m1RefForElements[0]+","+m1RefForElements[1];
                                        List <Message> m11List = allMessages.get(m11_orig);
                                        //System.out.println("\t\t\tm11List"+m11List);
                                        if(m11List!=null){
                                            addToAppliedMessageSet(m11List, host);
                                        }
                                    }
                                }
                            }
                        break;
                    }
                }
                if(flag==1){
                    index++;
                }
                else{
                    break;
                }
            }
        }
        
        public void applyToMessageList(Message m){
            String mRegion = getOriginAddressFromText(m)[1];
            List <Message> mRegionList = new ArrayList<>();
            if(appliedMessagesLists.containsKey(mRegion)){
                mRegionList = appliedMessagesLists.get(mRegion);
            }
            for(Message m1: mRegionList){
                if(getOriginAddressFromText(m)[0].equals(getOriginAddressFromText(m1)[0])
                        && getOriginAddressFromText(m)[2].equals(getOriginAddressFromText(m1)[2])){
                    return;
                }
            }
            mRegionList.add(m);
            appliedMessagesLists.put(mRegion, mRegionList);
        }
        
        public List <String> findAndAddToHoles (String from_str, String region_str, 
                List<String> holes, List<Message> mList, int maxSeqNum){            
            //System.out.println("\t\tmList: "+mList+ " maxSeqNum "+ maxSeqNum);
            for(int index2 = 1; index2<=maxSeqNum; index2++){
                int flag2=0;
                for(Message m3: mList){
                    if(getSeqNumFromText(m3)==index2){
                        flag2=1;//exists
                        break;
                    }
                }
                if(flag2==0){//index2 does not exist
                    holes.add("-"+from_str+","+region_str+","+index2);//add to holes
                }
            }
            return holes;
        }
        
        public List<String> extractRefsFromText(Message m){
            String text = m.toString();
            String [] allElements = text.split(";");
            List<String> refs = new ArrayList<>();
            for(int i=1; i<allElements.length; i++){
                refs.add(allElements[i]);
            }
            return refs;
        }
        
        public Message createNewPing(DTNHost host){
            List <String> referencesToAdd = new ArrayList<>(); // "-IdRegionSeqNum"
            //double r1 = Math.random(); //random for primary region
            //int primaryRegion = (int)(r1 * (NUM_OF_REGIONS));
            int primaryRegion = Integer.parseInt(host.getNs().getRandomRegionToPublish());
            System.out.print(" P_REG:"+primaryRegion+" ");
            referencesToAdd = addReferences(primaryRegion, referencesToAdd);
            double t = Math.random(); // random for twoReg prob
            if(t<twoRegionProb){
                double r2 = Math.random(); // random for secondary region
                int secondaryRegion = (int)(r2 * (NUM_OF_REGIONS));
                if(primaryRegion == secondaryRegion){// we want the two to be different
                    secondaryRegion = NUM_OF_REGIONS - primaryRegion;
                }
                System.out.print(" S_REG:"+secondaryRegion+ " ");
                referencesToAdd = addReferences(secondaryRegion, referencesToAdd);
            }
            int seqNum;
            if(!createdSeq.containsKey(primaryRegion)){
                seqNum =1; // start every sequence number from 1
            }
            else{
                seqNum = createdSeq.get(primaryRegion)+1;
            }     
            createdSeq.put(primaryRegion, seqNum);
            String id = "ping-" + host.getAddress() 
                            + "," + primaryRegion + "," + seqNum;
            for(String ref: referencesToAdd){
                if(ref.startsWith("-"+host.getAddress()+","+Integer.toString(primaryRegion))){//don't add any implicit ref
                    continue;
                }
                id=id+";"+ref;
            }
            Message m = new Message(host, randomHost(), id,getPingSize());
            //System.out.println("\treferences: "+referencesToAdd);
            m.addProperty("type", "ping");
            m.setAppID(APP_ID);
            
            
            return m;
            
        }
        
        public List<String> addReferences(int region, List<String> existingRefs){// add references for this region to create ping
            List <String> updatedRefs = existingRefs;
            for(String origin: allMessages.keySet()){
                String [] elements = origin.split(",");
                if(elements[1].equals(Integer.toString(region))){
                    //System.out.print(" "+origin+" "+findMaxSeqNumInApplied(origin)+" ");
                    updatedRefs.add("-"+origin+","+Integer.toString(findMaxSeqNumInApplied(origin)));
                }
            }
            //****optimize: remove in case of orderability
            Set <String> refsToRemove = new HashSet<>();
            for(String ref1: updatedRefs){
                for(String ref2: updatedRefs){
                    if(!ref1.equals(ref2) && ref2.contains(ref1)){// if ref 1 a reference for ref2
                        refsToRemove.add(ref1);
                    }
                }
            }
            for(String refToRemove: refsToRemove){
                updatedRefs.remove(refToRemove);
            }
            //System.out.println("\t\t\tRefsRemoved: "+refsToRemove+" AllRefs: "+updatedRefs);
            //***
            return updatedRefs;
        }
        
        public int findMaxSeqNumInApplied(String from){//max seq num for an origin(user+Region) in Applied
            int maxApplied=1;
            int max = findMaxSeqNumInMap(from);
            List <Message> mList = new ArrayList<>();
            mList = allMessages.get(from);
            for(int i=1; i<max; i++){
                for(Message m: mList){
                    if(getSeqNumFromText(m)==i){
                        maxApplied++;
                        break;
                    }
                }
            }
            return maxApplied;
        }
        
        
        public void updateReferencesMaps(Message m){
            List<String> refs = new ArrayList<>();
            String text = m.toString();
            String [] allElements = text.split(";");
            String [] elements = allElements[0].split("-");
            String [] originElements = elements[elements.length-1].split(",");
            String m1= originElements[0]+","+originElements[1]+","+originElements[2];// m1 is id of m
            for(int i=1; i<allElements.length; i++){
                String ref = allElements[i];
                String thisRef = ref.replace("-", "");
                refs.add(thisRef);
            }
            
            //1. update refernces map
            if(!references.containsKey(m1)){
                references.put(m1, refs);
            }
            
            //2. update referenceFor map
            for(String thisRef: refs){
                if(!referenceFor.containsKey(thisRef)){
                    List <String> thisRefList = new ArrayList<>();
                    thisRefList.add(m1);
                    referenceFor.put(thisRef, thisRefList);
                }
                else{
                    List<String> thisRefsFor = referenceFor.get(thisRef);
                    if(!thisRefsFor.contains(m1)){
                        thisRefsFor.add(m1);
                        referenceFor.put(thisRef, thisRefsFor);
                    }
                    
                }            
            }
            
            //System.out.println("\t\treferences: "+references);
            //System.out.println("\t\treferncesFor: "+referenceFor);
            
            //add empty list for each ref
            for(String thisRef: refs){
                String [] refElements = thisRef.split(",");
                if(!allMessages.containsKey(refElements[0]+","+refElements[1])){
                    List <Message> newList = new ArrayList<>();
                    allMessages.put(refElements[0]+","+refElements[1], newList);
                }
            }
        }
        
        public boolean appliedContains(String idRegSeq){
            for(Message m: appliedMessageSet){
                if(m.toString().startsWith("ping-"+idRegSeq) || m.toString().startsWith("response-"+idRegSeq)){
                    return true;
                }
            }
            return false;
        }
        
        public int countInterestedLevel0(DTNHost host){
            int c = 0;
            for(String s: allMessages.keySet()){
                String [] sElems =  s.split(",");
                String sReg = sElems[1];
                if(host.getNs().interestedRegion(sReg, 0, true)){
                    c+= allMessages.get(s).size();
                }
            }
            return c;
        }

        public int countInterestedLevel1(DTNHost host){
            int c = 0;
            for(Message m: appliedMessageSet){
                if(host.getNs().messageMatchesInterest(m, 1, true)){
                    c++;
                }
            }
            return c;
        }        
}
