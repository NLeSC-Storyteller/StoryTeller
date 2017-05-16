package vu.cltl.storyteller.json;

import com.hp.hpl.jena.rdf.model.Statement;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import vu.cltl.storyteller.input.EsoReader;
import vu.cltl.storyteller.input.EuroVoc;
import vu.cltl.storyteller.input.FrameNetReader;
import vu.cltl.storyteller.objects.PerspectiveJsonObject;
import vu.cltl.storyteller.objects.PhraseCount;
import vu.cltl.storyteller.objects.TrigTripleData;
import vu.cltl.storyteller.util.Util;

import java.util.*;

/**
 * Created by piek on 17/02/16.
 */
public class JsonStoryUtil {

    static public int nMergedEvents = 0;

    public static String getValidTimeAnchor (String time) {
        String timeAnchor = "";
        int idx = time.lastIndexOf("/");
        // http://www.newsreader-project.eu/time/20150126
        // idx = 37
        if (idx > -1 && idx<time.length()-1) {
            timeAnchor = time.substring(idx + 1);
            //timeAnchor = 20150126
        }
        if (!timeAnchor.isEmpty()) {
            ////// we need at least have the year!!!!
            ///// this check does not work for historic data!!!!
            if (timeAnchor.length() < 4) {
                timeAnchor = "";
            }
            else if (timeAnchor.length() == 6) {
                //// this is a month so we pick the first day of the month
                timeAnchor += "01";
            }
            else if (timeAnchor.length() == 4) {
                //// this is a year so we pick the first day of the year
                timeAnchor += "0101";
            }
            else if (timeAnchor.length() == 3 || timeAnchor.length() == 5 || timeAnchor.length() == 7) {
                ///date error, e.g. 12-07-198"
                timeAnchor = "";
            }

            ///skipping historic events
            if (!timeAnchor.startsWith("19") && !timeAnchor.startsWith("20")) {
                timeAnchor = "";
            }

            if (!timeAnchor.isEmpty()) {
                Integer dateInteger = Integer.parseInt(timeAnchor.substring(0, 4));
                if (dateInteger < 1999 || dateInteger > 2050) {
                    timeAnchor = "";
                }
            }
        }
        return timeAnchor;
    }


    public static ArrayList<JSONObject> getJSONObjectArray(TrigTripleData trigTripleData) throws JSONException {
        Vector<String> coveredEventInstances = new Vector<String>();
        ArrayList<JSONObject> jsonObjectArrayList = new ArrayList<JSONObject>();
        Set keySet = trigTripleData.tripleMapInstances.keySet();
        Iterator<String> keys = keySet.iterator();
        while (keys.hasNext()) {
            String key = keys.next(); //// this is the subject of the triple which should point to an event
            if (!coveredEventInstances.contains(key)) {
                coveredEventInstances.add(key);
                if (trigTripleData.tripleMapOthers.containsKey(key)) {
                    ArrayList<Statement> instanceTriples = trigTripleData.tripleMapInstances.get(key);
                    ArrayList<Statement> otherTriples = trigTripleData.tripleMapOthers.get(key);
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("instance", key); /// needs to be the full key otherwise not unique
                    String timeAnchor = JsonStoryFromRdf.getTimeAnchor(trigTripleData.tripleMapInstances, otherTriples);
                    timeAnchor = getValidTimeAnchor(timeAnchor);
                    if (!timeAnchor.isEmpty()) {
                        try {
                            jsonObject.put("time", timeAnchor);
                            JSONObject jsonClasses = JsonStoryFromRdf.getClassesJSONObjectFromInstanceStatement(instanceTriples);
                            if (jsonClasses.keys().hasNext()) {
                                jsonObject.put("classes", jsonClasses);
                            }
                            JSONObject jsonLabels = JsonStoryFromRdf.getLabelsJSONObjectFromInstanceStatement(instanceTriples);
                            if (jsonLabels.keys().hasNext()) {
                                jsonObject.put("labels", jsonLabels.get("labels"));
                            }
                            JSONObject jsonprefLabels = JsonStoryFromRdf.getPrefLabelsJSONObjectFromInstanceStatement(instanceTriples);
                            if (jsonprefLabels.keys().hasNext()) {
                                jsonObject.put("prefLabel", jsonprefLabels.get("prefLabel"));
                            }
                            JSONObject jsonMentions = JsonStoryFromRdf.getMentionsJSONObjectFromInstanceStatement(instanceTriples);
                            if (jsonMentions.keys().hasNext()) {
                                jsonObject.put("mentions", jsonMentions.get("mentions"));
                            }
                            JSONObject actors = JsonStoryFromRdf.getActorsJSONObjectFromInstanceStatement(otherTriples);
                            if (actors.keys().hasNext()) {
                                jsonObject.put("actors", actors);
                            }
                            JSONObject topics = JsonStoryFromRdf.getTopicsJSONObjectFromInstanceStatement(instanceTriples);
                            if (topics.keys().hasNext()) {
                                jsonObject.put("topics", topics.get("topics"));
                            }
                            jsonObjectArrayList.add(jsonObject);
                        } catch (NumberFormatException e) {
                            // e.printStackTrace();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return jsonObjectArrayList;
    }

    public static ArrayList<JSONObject> getJSONObjectArray(TrigTripleData trigTripleData,
                                                           boolean ALL,
                                                           boolean SKIPPERSPECTIVEEVENTS,
                                                           String eventTypes,
                                                           ArrayList<String> blacklist,
                                                           HashMap<String, ArrayList<String>> iliMap,
                                                           int fnLevel,
                                                           FrameNetReader frameNetReader,
                                                           ArrayList<String> topFrames,
                                                           int esoLevel,
                                                           EsoReader esoReader) throws JSONException {
        Vector<String> coveredEventInstances = new Vector<String>();
        ArrayList<JSONObject> jsonObjectArrayList = new ArrayList<JSONObject>();
        int nSkip = 0;
        Set keySet = trigTripleData.tripleMapInstances.keySet();
        Iterator<String> keys = keySet.iterator();
        while (keys.hasNext()) {
            String key = keys.next(); //// this is the subject of the triple which should point to an event
            if (!coveredEventInstances.contains(key)) {
                coveredEventInstances.add(key);
                if (trigTripleData.tripleMapOthers.containsKey(key)) {
                    // System.out.println("key = " + key);
                    ArrayList<Statement> instanceTriples = trigTripleData.tripleMapInstances.get(key);
                    if (JsonStoryFromRdf.prefLabelInList(instanceTriples, blacklist)) {
                        continue;
                    }
                    if (SKIPPERSPECTIVEEVENTS) {
                        /// we skip events that are objects of the generatedBy property in the grasp layer
                       if (JsonStoryFromRdf.mentionInList(instanceTriples, trigTripleData.perspectiveMentions)) {
                            nSkip++;
                            continue;
                        }
                    }
                    if (eventTypes.isEmpty() ||
                            eventTypes.equalsIgnoreCase("N") ||
                            eventTypes.equalsIgnoreCase("any") ||
                            JsonStoryFromRdf.matchEventType(instanceTriples, eventTypes)) {
                        /// this means it is an instance and has semrelations
                        ArrayList<Statement> otherTriples = trigTripleData.tripleMapOthers.get(key);
                        if (JsonStoryFromRdf.hasActor(otherTriples) || ALL) {
                            /// we ignore events without actors.....
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("event", JsonStoryFromRdf.getValue(JsonStoryFromRdf.getSynsetsFromIli(key, iliMap)));
                            // jsonObject.put("instance", getValue(key));
                            jsonObject.put("instance", key); /// needs to be the full key otherwise not unique
                            String timeAnchor = JsonStoryFromRdf.getTimeAnchor(trigTripleData.tripleMapInstances, otherTriples);
                            //System.out.println("timeAnchor = " + timeAnchor);
                            if (timeAnchor.isEmpty()) {
                                continue;
                            }
                            int idx = timeAnchor.lastIndexOf("/");
                            if (idx > -1) {
                                timeAnchor = timeAnchor.substring(idx + 1);
                            }
                            ////// we need at least have the year!!!!
                            ///// this check does not work for historic data!!!!
                            if (timeAnchor.length() < 4) {
                                continue;
                            }
                            if (timeAnchor.length() == 6) {
                                //// this is a month so we pick the first day of the month
                                timeAnchor += "01";
                            }
                            if (timeAnchor.length() == 4) {
                                //// this is a year so we pick the first day of the year
                                timeAnchor += "0101";
                            }
                            if (timeAnchor.length() == 3 || timeAnchor.length() == 5 || timeAnchor.length() == 7) {
                                ///date error, e.g. 12-07-198"
                                continue;
                            }
                            ///skipping historic events
                            /* if (timeAnchor.startsWith("19") || timeAnchor.startsWith("20")) {
                                continue;
                               }
                            */
                            try {
                                //System.out.println("timeAnchor = " + timeAnchor);
                                Integer dateInteger = Integer.parseInt(timeAnchor.substring(0,4));
                                if (dateInteger>1999 && dateInteger<2050) {
                                //if (timeAnchor.startsWith("20")) {
                                    jsonObject.put("time", timeAnchor);
                                    JSONObject jsonClasses = JsonStoryFromRdf.getClassesJSONObjectFromInstanceStatement(instanceTriples);
                                    if (jsonClasses.keys().hasNext()) {
                                        jsonObject.put("classes", jsonClasses);
                                    }
                                    if (fnLevel > 0) {
                                        JsonStoryFromRdf.getFrameNetSuperFramesJSONObjectFromInstanceStatement(frameNetReader, topFrames, jsonObject, instanceTriples);
                                    } else if (esoLevel > 0) {
                                        JsonStoryFromRdf.getEsoSuperClassesJSONObjectFromInstanceStatement(esoReader, esoLevel, jsonObject, instanceTriples);
                                    }

                                    JSONObject jsonLabels = JsonStoryFromRdf.getLabelsJSONObjectFromInstanceStatement(instanceTriples);
                                    if (jsonLabels.keys().hasNext()) {
                                        jsonObject.put("labels", jsonLabels.get("labels"));
                                    }
                                    JSONObject jsonprefLabels = JsonStoryFromRdf.getPrefLabelsJSONObjectFromInstanceStatement(instanceTriples);
                                    if (jsonprefLabels.keys().hasNext()) {
                                        jsonObject.put("prefLabel", jsonprefLabels.get("prefLabel"));
                                    }
                                    JSONObject jsonMentions = JsonStoryFromRdf.getMentionsJSONObjectFromInstanceStatement(instanceTriples);
                                    if (jsonMentions.keys().hasNext()) {
                                        jsonObject.put("mentions", jsonMentions.get("mentions"));
                                    }
                                    JSONObject actors = JsonStoryFromRdf.getActorsJSONObjectFromInstanceStatement(otherTriples, blacklist);
                                    //JSONObject actors = JsonFromRdf.getActorsJSONObjectFromInstanceStatementSimple(otherTriples);
                                    if (actors.keys().hasNext()) {
                                        jsonObject.put("actors", actors);
                                    }
                                    JSONObject topics = JsonStoryFromRdf.getTopicsJSONObjectFromInstanceStatement(instanceTriples);
                                    if (topics.keys().hasNext()) {
                                        //  System.out.println("topics.length() = " + topics.length());
                                        jsonObject.put("topics", topics.get("topics"));
                                    }
                                    jsonObjectArrayList.add(jsonObject);
                                }
                            } catch (NumberFormatException e) {
                               // e.printStackTrace();
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        } else {
                            //  System.out.println("no actor relations in otherTriples.size() = " + otherTriples.size());
                        }
                    } else {
                        //// wrong event types
                    //    System.out.println("eventTypes = " + eventTypes);
                    //    System.out.println("key = " + key);
                        JSONObject jsonprefLabels = JsonStoryFromRdf.getPrefLabelsJSONObjectFromInstanceStatement(instanceTriples);
                     //   System.out.println("jsonprefLabels.toString() = " + jsonprefLabels.toString());

                    }
                } else {
                    //  System.out.println("No sem relations for = " + key);
                }
            }
        }
       // System.out.println("Nr. of perspective generatedBy Events skipped = " + nSkip);
        return jsonObjectArrayList;
    }

    /**
     * Removes actors that are below the frequency threshold
     * @param events
     * @param entityFilter
     * @param actorThreshold
     * @return
     * @throws JSONException
     */
    public static ArrayList<JSONObject> filterEventsForActors(ArrayList<JSONObject> events,
                                                              String entityFilter,
                                                              int actorThreshold) throws JSONException {
        ArrayList<JSONObject> jsonObjectArrayList = new ArrayList<JSONObject>();
        /*
        "actors":{"pb/A0":["http://www.newsreader-project.eu/data/timeline/non-entities/to_a_single_defense_contractor"]}
         */
        HashMap <String, Integer> actorCount  = JsonStoryUtil.createActorCount(events);
        for (int i = 0; i < events.size(); i++) {
            JSONObject oEvent = events.get(i);
            boolean hasActorCount = false;
            JSONObject oActorObject = null;
            try {
                oActorObject = oEvent.getJSONObject("actors");
                Iterator oKeys = oActorObject.sortedKeys();
                while (oKeys.hasNext()) {
                    String oKey = oKeys.next().toString();
                    try {
                        JSONArray actors = oActorObject.getJSONArray(oKey);
                        for (int j = 0; j < actors.length(); j++) {
                            String actor = actors.getString(j);
                            actor = actor.substring(actor.lastIndexOf("/") + 1);
                            if ((actor.length()>1)) {
                                if (actorCount.containsKey(actor)) {
                                    Integer cnt = actorCount.get(actor);
                                    if (cnt >= actorThreshold) {
                                        hasActorCount = true;
                                        // System.out.println("actor = " + actor);
                                        // System.out.println("cnt = " + cnt);
                                    } else {
                                        /// removes actors with too low freqency
                                        oActorObject.remove(oKey);
                                    }
                                } else {
                                    /// removes actors with too low freqency
                                    oActorObject.remove(oKey);
                                }
                            }
                            else {
                                /// removes too short words
                                oActorObject.remove(oKey);
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            } catch (JSONException e) {
                // e.printStackTrace();
            }
            if (hasActorCount) {
                try {
                    oActorObject = oEvent.getJSONObject("actors");
                    if (oActorObject.length()>0) {
                        ///// make sure we have actors left
                        //  System.out.println("Adding oEvent.toString() = " + oEvent.get("labels").toString());
                        jsonObjectArrayList.add(oEvent);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            else {
            }
        }
        return jsonObjectArrayList;
    }

    static void removeTinyActors(ArrayList<JSONObject> events) throws JSONException {
        /*
        "actors":{"pb/A0":["http://www.newsreader-project.eu/data/timeline/non-entities/to_a_single_defense_contractor"]}
         */
        for (int i = 0; i < events.size(); i++) {
            JSONObject oEvent = events.get(i);
            JSONObject oActorObject = null;
            try {
                oActorObject = oEvent.getJSONObject("actors");
                Iterator oKeys = oActorObject.sortedKeys();
                while (oKeys.hasNext()) {
                    String oKey = oKeys.next().toString();
                    try {
                        JSONArray actors = oActorObject.getJSONArray(oKey);
                        JSONArray filteredActors = new JSONArray();
                        for (int j = 0; j < actors.length(); j++) {
                            String actor = actors.getString(j);
                            actor = actor.substring(actor.lastIndexOf("/") + 1);
                            if ((actor.length()<8) && actor.toLowerCase().equals(actor)) {
                                /// removes too short words
                                oActorObject.remove(oKey);
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                oEvent.put("actors", oActorObject);
            } catch (JSONException e) {
                // e.printStackTrace();
            }
        }
    }

    public static ArrayList<JSONObject> filterEventsForBlackList(ArrayList<JSONObject> events, ArrayList<String> blacklist) throws JSONException {
        ArrayList<JSONObject> jsonObjectArrayList = new ArrayList<JSONObject>();
        for (int i = 0; i < events.size(); i++) {
            JSONObject jsonObject = events.get(i);
            JSONArray labels = null;
            try {
                labels = (JSONArray) jsonObject.get("labels");
            } catch (JSONException e) {
              //  e.printStackTrace();
            }
            if (labels != null) {
                for (int j = 0; j < labels.length(); j++) {
                    String label = labels.getString(j);
                    // System.out.println("label = " + label);
                    if (!blacklist.contains(label)) {
                        jsonObjectArrayList.add(jsonObject);
                        break;
                    }
                }

            } else {
              /////
            }
        }
        return jsonObjectArrayList;
    }


    public static ArrayList<JSONObject> createStoryLinesForJSONArrayList(ArrayList<JSONObject> jsonObjects, int climaxThreshold, int topicThreshold
    )  throws JSONException {
        boolean DEBUG = false;
        ArrayList<JSONObject> groupedObjects = new ArrayList<JSONObject>(); /// keeps track which events are already in a story so that the same event does not end up in multiple stories
        ArrayList<JSONObject> singletonObjects = new ArrayList<JSONObject>(); /// keeps track of singleton stories, without bridging relations
        ArrayList<JSONObject> selectedEvents  = new ArrayList<JSONObject>();/// set of events above the climax threshold sorted according to this threshold
        ArrayList<JSONObject> storyObjects = new ArrayList<JSONObject>(); /// data struture for a story

        /// We build up a climax index over all the events
        //Vector<Integer> climaxIndex = new Vector<Integer>();
        //1. We determine the climax score for each individual event
        // We sum the inverse sentence numbers of all mentions
        TreeSet climaxObjects = determineClimaxValues(jsonObjects, climaxThreshold);
        Iterator<JSONObject> sortedObjects = climaxObjects.iterator();
        while (sortedObjects.hasNext()) {
            JSONObject jsonObject = sortedObjects.next();
            selectedEvents.add(jsonObject);
        }
      //  System.out.println("Events above climax threshold = " + climaxObjects.size());
        sortedObjects = climaxObjects.iterator();
        ArrayList<String> coveredEvents = new ArrayList<String>();
        int eventCounter = 0;
        while (sortedObjects.hasNext()) {
            JSONObject jsonObject = sortedObjects.next();
            eventCounter++;
            String instance = jsonObject.getString("instance");
            if (!coveredEvents.contains(instance)) {
                coveredEvents.add(instance);
                //// this event is not yet part of a story and is the next event with climax value
                //// we use this to create a new story by adding other events with bridging relations into the storyObjects ArrayList
                try {
                    storyObjects = new ArrayList<JSONObject>(); /// initialise the ArrayList for the story events

                    /// create the administrative fields in the JSON structure for a event that define story membership
                    Integer groupClimax = Integer.parseInt(jsonObject.get("climax").toString());
                    String group = "";
                    String labels = "";
                    try {
                        labels = jsonObject.get("labels").toString();
                    } catch (JSONException e) {
                        try {
                            labels = jsonObject.get("prefLabel").toString();
                        } catch (JSONException e1) {
                           // e1.printStackTrace();
                        }
                       //  e.printStackTrace();
                    }
                    group = climaxString(groupClimax) + ":" + labels;

                    String groupName = labels;
                    String groupScore = climaxString(groupClimax);
                    String mainActor = getfirstActorByRoleFromEvent(jsonObject, "pb/A1"); /// for representation purposes
                    if (mainActor.isEmpty()) {
                        mainActor = getfirstActorByRoleFromEvent(jsonObject, "pb/A0");
                    }
                    if (mainActor.isEmpty()) {
                        mainActor = getfirstActorByRoleFromEvent(jsonObject, "pb/A2");
                    }
                    group += mainActor;
                    groupName += mainActor;
                    jsonObject.put("group", group);
                    jsonObject.put("groupName", groupName);
                    jsonObject.put("groupScore", groupScore);
/*                    labels += mainActor;
                    jsonObject.put("prefLabel", labels);*/

                    //// add the climax event to the story ArrayList
                    storyObjects.add(jsonObject);


                    //// now we look for other events with bridging relations
                    ArrayList<JSONObject> bridgedEvents = new ArrayList<JSONObject>();

                    ArrayList<JSONObject> coevents =  new ArrayList<JSONObject>();
                    coevents = getEventsThroughCoparticipation(selectedEvents, jsonObject, 1);
                    ArrayList<JSONObject> topicevents = new ArrayList<JSONObject>();
                    topicevents = getEventsThroughTopicBridging(selectedEvents, jsonObject, topicThreshold);


                    //// strict variant: there must be overlap of participants and topics
                    if (topicThreshold>0) {
                        bridgedEvents = intersectEventObjects(coevents, topicevents);
                        if (bridgedEvents.size() > 5) {
                           // System.out.println("intersection co-participating events and topical events = " + bridgedEvents.size());
                            int prop = (100*coveredEvents.size())/climaxObjects.size();
                          //  System.out.println("covered Events = " + prop +"%");
                        }
                    }
                    else {
                        bridgedEvents = coevents;
                        if (bridgedEvents.size() > 5) {
                          //  System.out.println("intersection co-participating events = " + bridgedEvents.size());
                            int prop = (100*coveredEvents.size())/climaxObjects.size();
                         //   System.out.println("covered Events = " + prop +"%");
                        }
                    }


                    for (int i = 0; i < bridgedEvents.size(); i++) {
                        JSONObject object = bridgedEvents.get(i);
                        String eventInstance = object.getString("instance");
                        if (!coveredEvents.contains(eventInstance)) {
                        //if (!hasObject(groupedObjects, object)) {
                            addObjectToGroup(
                                    storyObjects,
                                    group,
                                    groupName,
                                    groupScore,
                                    object,
                                    8,
                                    climaxThreshold);
                            selectedEvents.remove(object); /// no longer available for linking
                           // System.out.println("selectedEvents = " + selectedEvents.size());
                            coveredEvents.add(eventInstance);
                        }
                        else {
                            ///// this means that the bridged event was already consumed by another story
                            ///// that's a pity for this story. It cannot be used anymore.
                        }
                    }


                    if (storyObjects.size()>1) {
                        for (int i = 0; i < storyObjects.size(); i++) {
                            JSONObject object = storyObjects.get(i);
                            groupedObjects.add(object);
                        }
                    }
                    else {
                        for (int i = 0; i < storyObjects.size(); i++) {
                            JSONObject object = storyObjects.get(i);
                            //groupedObjects.add(object);
                            singletonObjects.add(object);
                        }
                    }

                } catch (JSONException e) {
                     e.printStackTrace();
                }
            }
            else {
            //    System.out.println("duplicate instance = " + instance);
            }
            //  break;

        } // end of while objects in sorted climaxObjects

        //// now we handle the singleton events
        storyObjects = new ArrayList<JSONObject>(); /// initialise the ArrayList for the story events
        String group = "001:unrelated events";
        String groupName = "unrelated events";
        String groupScore = "001";
        for (int i = 0; i < singletonObjects.size(); i++) {
            JSONObject jsonObject = singletonObjects.get(i);
            jsonObject.put("group", group);
            jsonObject.put("groupName", groupName);
            jsonObject.put("groupScore", groupScore);
            addObjectToGroup(
                    storyObjects,
                    group,
                    groupName,
                    groupScore,
                    jsonObject,
                    2,
                    climaxThreshold);
        }
      //  System.out.println("groupedObjects.size() = " + groupedObjects.size());
      //  System.out.println("singleObjects.size() = " + storyObjects.size());
        //// we add the singleton events to the other grouped events
        for (int i = 0; i < storyObjects.size(); i++) {
            JSONObject object = storyObjects.get(i);
            groupedObjects.add(object);
        }
       // System.out.println("eventCounter = " + eventCounter);
        return groupedObjects;
    }


    public static ArrayList<JSONObject> createStoryLinesForJSONArrayList(ArrayList<JSONObject> jsonObjects,
                                                                         int topicThreshold,
                                                                         int climaxThreshold,
                                                                         String entityFilter,
                                                                         boolean MERGE,
                                                                         String timeGran,
                                                                         String actionOnt,
                                                                         int actionSim,
                                                                         int interSect
    )  throws JSONException {
        boolean DEBUG = false;
        ArrayList<JSONObject> groupedObjects = new ArrayList<JSONObject>(); /// keeps track which events are already in a story so that the same event does not end up in multiple stories
        ArrayList<JSONObject> singletonObjects = new ArrayList<JSONObject>(); /// keeps track of singleton stories, without bridging relations
        ArrayList<JSONObject> selectedEvents  = new ArrayList<JSONObject>();/// set of events above the climax threshold sorted according to this threshold
        ArrayList<JSONObject> storyObjects = new ArrayList<JSONObject>(); /// data struture for a story

        /// We build up a climax index over all the events
        //Vector<Integer> climaxIndex = new Vector<Integer>();
        //1. We determine the climax score for each individual event
        // We sum the inverse sentence numbers of all mentions
        TreeSet climaxObjects = determineClimaxValues(jsonObjects, climaxThreshold);
        //TreeSet climaxObjects = determineClimaxValuesFirstMentionOnly(jsonObjects);
        Iterator<JSONObject> sortedObjects = climaxObjects.iterator();
        while (sortedObjects.hasNext()) {
            JSONObject jsonObject = sortedObjects.next();
            selectedEvents.add(jsonObject);
        }
      //  System.out.println("Events above climax threshold = " + climaxObjects.size());
        sortedObjects = climaxObjects.iterator();
        ArrayList<String> coveredEvents = new ArrayList<String>();
        int eventCounter = 0;
        while (sortedObjects.hasNext()) {
            JSONObject jsonObject = sortedObjects.next();
            eventCounter++;
            String instance = jsonObject.getString("instance");
            if (!coveredEvents.contains(instance)) {
                coveredEvents.add(instance);
                //// this event is not yet part of a story and is the next event with climax value
                //// we use this to create a new story by adding other events with bridging relations into the storyObjects ArrayList
                try {
                    storyObjects = new ArrayList<JSONObject>(); /// initialise the ArrayList for the story events

                    /// create the administrative fields in the JSON structure for a event that define story membership
                    Integer groupClimax = Integer.parseInt(jsonObject.get("climax").toString());
                    String group = "";
                    String labels = "";
                    try {
                        labels = jsonObject.get("labels").toString();
                    } catch (JSONException e) {
                        try {
                            labels = jsonObject.get("prefLabel").toString();
                        } catch (JSONException e1) {
                           // e1.printStackTrace();
                        }
                       //  e.printStackTrace();
                    }
                    group = climaxString(groupClimax) + ":" + labels;

                    String groupName = labels;
                    String groupScore = climaxString(groupClimax);
                    String mainActor = getfirstActorByRoleFromEvent(jsonObject, "pb/A1"); /// for representation purposes
                    if (mainActor.isEmpty()) {
                        mainActor = getfirstActorByRoleFromEvent(jsonObject, "pb/A0");
                    }
                    if (mainActor.isEmpty()) {
                        mainActor = getfirstActorByRoleFromEvent(jsonObject, "pb/A2");
                    }
                    group += mainActor;
                    groupName += mainActor;
                    jsonObject.put("group", group);
                    jsonObject.put("groupName", groupName);
                    jsonObject.put("groupScore", groupScore);
/*                    labels += mainActor;
                    jsonObject.put("prefLabel", labels);*/

                    //// add the climax event to the story ArrayList
                    storyObjects.add(jsonObject);


                    //// now we look for other events with bridging relations
                    ArrayList<JSONObject> bridgedEvents = new ArrayList<JSONObject>();

                    ArrayList<JSONObject> coevents =  new ArrayList<JSONObject>();
                    if (entityFilter.isEmpty()) {
                        coevents = getEventsThroughCoparticipation(selectedEvents, jsonObject, interSect);
                    }
                    else {
                        coevents = getEventsThroughCoparticipation(entityFilter, selectedEvents, jsonObject);
                    }
                    ArrayList<JSONObject> topicevents = new ArrayList<JSONObject>();
                    if (topicThreshold>0) {
                        topicevents = getEventsThroughTopicBridging(selectedEvents, jsonObject, topicThreshold);
                    }

                    //// strict variant: there must be overlap of participants and topics
                    if (topicThreshold>0) {
                        bridgedEvents = intersectEventObjects(coevents, topicevents);
                        if (bridgedEvents.size() > 5) {
                           // System.out.println("intersection co-participating events and topical events = " + bridgedEvents.size());
                            int prop = (100*coveredEvents.size())/climaxObjects.size();
                          //  System.out.println("covered Events = " + prop +"%");
                        }
                    }
                    else {
                        bridgedEvents = coevents;
                        if (bridgedEvents.size() > 5) {
                          //  System.out.println("intersection co-participating events = " + bridgedEvents.size());
                            int prop = (100*coveredEvents.size())/climaxObjects.size();
                         //   System.out.println("covered Events = " + prop +"%");
                        }
                    }


                    for (int i = 0; i < bridgedEvents.size(); i++) {
                        JSONObject object = bridgedEvents.get(i);
                        String eventInstance = object.getString("instance");
                        if (!coveredEvents.contains(eventInstance)) {
                        //if (!hasObject(groupedObjects, object)) {
                            addObjectToGroup(
                                    storyObjects,
                                    group,
                                    groupName,
                                    groupScore,
                                    object,
                                    8,
                                    climaxThreshold);
                            selectedEvents.remove(object); /// no longer available for linking
                           // System.out.println("selectedEvents = " + selectedEvents.size());
                            coveredEvents.add(eventInstance);
                        }
                        else {
                            ///// this means that the bridged event was already consumed by another story
                            ///// that's a pity for this story. It cannot be used anymore.
                        }
                    }


                    if (storyObjects.size()>1) {
                        if (MERGE) {
                            JsonStoryUtil.nMergedEvents = 0;
                            storyObjects = JsonStoryUtil.mergeEvents(storyObjects, timeGran, actionOnt, actionSim);
                            //System.out.println("Nr. of merged events (proportional ontologology match: " + actionSim+", time: "+timeGran+") = " + JsonStoryUtil.nMergedEvents);
                        }
                        for (int i = 0; i < storyObjects.size(); i++) {
                            JSONObject object = storyObjects.get(i);
                            groupedObjects.add(object);
                        }
                    }
                    else {
                        for (int i = 0; i < storyObjects.size(); i++) {
                            JSONObject object = storyObjects.get(i);
                            //groupedObjects.add(object);
                            singletonObjects.add(object);
                        }
                    }

                } catch (JSONException e) {
                     e.printStackTrace();
                }
            }
            else {
            //    System.out.println("duplicate instance = " + instance);
            }
            //  break;

        } // end of while objects in sorted climaxObjects

        //// now we handle the singleton events
        storyObjects = new ArrayList<JSONObject>(); /// initialise the ArrayList for the story events
        String group = "001:unrelated events";
        String groupName = "unrelated events";
        String groupScore = "001";
        for (int i = 0; i < singletonObjects.size(); i++) {
            JSONObject jsonObject = singletonObjects.get(i);
            jsonObject.put("group", group);
            jsonObject.put("groupName", groupName);
            jsonObject.put("groupScore", groupScore);
            addObjectToGroup(
                    storyObjects,
                    group,
                    groupName,
                    groupScore,
                    jsonObject,
                    2,
                    climaxThreshold);
        }
      //  System.out.println("groupedObjects.size() = " + groupedObjects.size());
      //  System.out.println("singleObjects.size() = " + storyObjects.size());
        //// we add the singleton events to the other grouped events
        for (int i = 0; i < storyObjects.size(); i++) {
            JSONObject object = storyObjects.get(i);
            groupedObjects.add(object);
        }
       // System.out.println("eventCounter = " + eventCounter);
        return groupedObjects;
    }

    public static ArrayList<JSONObject> createOneStoryForJSONArrayList(ArrayList<JSONObject> jsonObjects,
                                                                       int climaxThreshold,
                                                                       boolean MERGE,
                                                                       String timeGran,
                                                                       String actionOnt,
                                                                       int actionSim
    )  throws JSONException {
        boolean DEBUG = false;
        ArrayList<JSONObject> storyObjects = new ArrayList<JSONObject>(); /// data struture for a story

        /// We build up a climax index over all the events
        //Vector<Integer> climaxIndex = new Vector<Integer>();
        //1. We determine the climax score for each individual event
        // We sum the inverse sentence numbers of all mentions
        TreeSet climaxObjects = determineClimaxValues(jsonObjects, climaxThreshold);
        //TreeSet climaxObjects = determineClimaxValuesFirstMentionOnly(jsonObjects);
        Iterator<JSONObject> sortedObjects = climaxObjects.iterator();
        System.out.println("Events above climax threshold = " + climaxObjects.size());
        sortedObjects = climaxObjects.iterator();
        ArrayList<String> coveredEvents = new ArrayList<String>();

        ///Main event....
        JSONObject mainEvent = sortedObjects.next();
        /// create the administrative fields in the JSON structure for a event that define story membership
        Integer groupClimax = Integer.parseInt(mainEvent.get("climax").toString());
        String group = "";
        String labels = "";
        try {
            labels = mainEvent.get("labels").toString();
        } catch (JSONException e) {
            try {
                labels = mainEvent.get("prefLabel").toString();
            } catch (JSONException e1) {
                // e1.printStackTrace();
            }
            //  e.printStackTrace();
        }
        group = climaxString(groupClimax) + ":" + labels;

        String groupName = labels;
        String groupScore = climaxString(groupClimax);
        String mainActor = getfirstActorByRoleFromEvent(mainEvent, "pb/A1"); /// for representation purposes
        if (mainActor.isEmpty()) {
            mainActor = getfirstActorByRoleFromEvent(mainEvent, "pb/A0");
        }
        if (mainActor.isEmpty()) {
            mainActor = getfirstActorByRoleFromEvent(mainEvent, "pb/A2");
        }
        group += mainActor;
        groupName += mainActor;
        mainEvent.put("group", group);
        mainEvent.put("groupName", groupName);
        mainEvent.put("groupScore", groupScore);
        String instance = mainEvent.getString("instance");
        coveredEvents.add(instance);

        //// add the climax event to the story ArrayList
        storyObjects.add(mainEvent);
        int eventCounter = 0;
        while (sortedObjects.hasNext()) {
            JSONObject jsonObject = sortedObjects.next();
            eventCounter++;
            instance = jsonObject.getString("instance");
            if (!coveredEvents.contains(instance)) {
                coveredEvents.add(instance);
                //// this event is not yet part of a story and is the next event with climax value
                //// we use this to create a new story by adding other events with bridging relations into the storyObjects ArrayList
                try {
                    addObjectToGroup(
                            storyObjects,
                            group,
                            groupName,
                            groupScore,
                            jsonObject,
                            8,
                            climaxThreshold);


                } catch (JSONException e) {
                     e.printStackTrace();
                }
            }
            else {
            //    System.out.println("duplicate instance = " + instance);
            }
            //  break;

        } // end of while objects in sorted climaxObjects




        if (MERGE) {
            JsonStoryUtil.nMergedEvents = 0;
            storyObjects = JsonStoryUtil.mergeEvents(storyObjects, timeGran, actionOnt, actionSim);
            //System.out.println("Nr. of merged events (proportional ontologology match: " + actionSim+", time: "+timeGran+") = " + JsonStoryUtil.nMergedEvents);
        }

        System.out.println("eventCounter = " + eventCounter);
        return storyObjects;
    }



    /**
     * Determines the climax values by summing the inverse values of the sentence nr of each mention
     * @param jsonObjects
     * @return
     */
    static TreeSet determineClimaxValues (ArrayList<JSONObject> jsonObjects, int climaxThreshold) {
        //1. We determine the climax score for each individual event and return a sorted list by climax
        // We sum the inverse sentence numbers of all mentions
        TreeSet climaxObjects = new TreeSet(new climaxCompare());
        Double maxClimax = -1.0;
        for (int i = 0; i < jsonObjects.size(); i++) {
            JSONObject jsonObject = jsonObjects.get(i);
            // System.out.println("jsonObject.toString() = " + jsonObject.toString());
            try {
                Double sumClimax =0.0;
                JSONArray mentions = (JSONArray) jsonObject.get("mentions");
                int earliestEventMention = -1;
                for (int j = 0; j < mentions.length(); j++) {
                    JSONObject mentionObject = (JSONObject) mentions.get(j);
                    // System.out.println("charValue = " + mentionObject.getString("char"));
                    // System.out.println("sentenceValue = " + mentionObject.getString("sentence"));
                    JSONArray sentences = null;
                    try {
                        sentences = mentionObject.getJSONArray("sentence");
                    } catch (JSONException e) {
                        //  e.printStackTrace();
                    }
                    if (sentences!=null ){
                        String sentenceValue = sentences.get(0).toString();
                        int sentenceNr = Integer.parseInt(sentenceValue);
                        if (sentenceNr < earliestEventMention || earliestEventMention == -1) {
                            earliestEventMention = sentenceNr;
                            jsonObject.put("sentence", sentenceValue);
                        }
                        sumClimax += 1.0 / sentenceNr;
                        //String mention = mentions.get(j).toString();
                    }
                    else {
                        JSONArray charValues = null;
                        try {
                            charValues = mentionObject.getJSONArray("char");
                        } catch (JSONException e) {
                            //     e.printStackTrace();
                        }
                        //"["4160","4165"]"
                        if (charValues!=null ) {
                            String charValue = charValues.get(0).toString();
                           // System.out.println("charValue = " + charValue);
                            int sentenceNr = 0;
                            try {
                                sentenceNr = Integer.parseInt(charValue);
                                if (sentenceNr < earliestEventMention || earliestEventMention == -1) {
                                    earliestEventMention = sentenceNr;
                                    jsonObject.put("sentence", charValue);
                                }
                                sumClimax += 1.0 / sentenceNr;
                            } catch (NumberFormatException e) {
                                e.printStackTrace();
                            }

                        }
                        else {
                            //   System.out.println("charValues = null");
                        }
                    }
                }
                sumClimax = Math.abs(Math.log10(sumClimax));
              //  System.out.println("sumClimax = " + sumClimax);
                if (Double.isInfinite(sumClimax)) {
                    jsonObject.put("climax", "0");
                }
                else {
                    if (sumClimax>maxClimax) {
                        maxClimax = sumClimax;
                    }
                    jsonObject.put("climax", sumClimax);
                }
            } catch (JSONException e) {
               //   e.printStackTrace();
                try {
                    jsonObject.put("climax", "0");
                } catch (JSONException e1) {
                 //   e1.printStackTrace();
                }

            }
        }
        //System.out.println("maxClimax = " + maxClimax);
        /// next we normalize the climax values and store it in the tree
        for (int i = 0; i < jsonObjects.size(); i++) {
            JSONObject jsonObject = jsonObjects.get(i);
            try {
                Double climax = Double.parseDouble(jsonObject.get("climax").toString());
                // Double proportion = Math.log(climax/maxClimax);
                Double proportion = climax/maxClimax;
                Integer climaxInteger = new Integer ((int)(100*proportion));
                if (climaxInteger==0) {
                    climaxInteger=1;
                }
                if (climaxInteger>=climaxThreshold) {
                    jsonObject.put("climax", climaxInteger);
                    climaxObjects.add(jsonObject);
                }

            } catch (JSONException e) {
                   e.printStackTrace();
            }
        }
        return climaxObjects;
    }

    /**
     * Determines the climax values by summing the inverse values of the sentence nr of each mention
     * @param jsonObjects
     * @return
     */
    static TreeSet determineClimaxValues_org_using_old_mention_structure (ArrayList<JSONObject> jsonObjects, int climaxThreshold) {
        //1. We determine the climax score for each individual event and return a sorted list by climax
        // We sum the inverse sentence numbers of all mentions
        TreeSet climaxObjects = new TreeSet(new climaxCompare());
        Double maxClimax = 0.0;
        for (int i = 0; i < jsonObjects.size(); i++) {
            JSONObject jsonObject = jsonObjects.get(i);
            try {
                double sumClimax =0.0;
                JSONArray mentions = (JSONArray) jsonObject.get("mentions");
                int earliestEventMention = -1;
                for (int j = 0; j < mentions.length(); j++) {
                    String mention =  mentions.get(j).toString();
                    int idx = mention.indexOf("sentence=");
                    if (idx >-1) {
                        idx = mention.lastIndexOf("=");
                        int sentenceNr = Integer.parseInt(mention.substring(idx+1));
                        if (sentenceNr<earliestEventMention || earliestEventMention==-1) {
                            earliestEventMention = sentenceNr;
                            jsonObject.put("sentence", mention.substring(idx + 1));
                        }
                        sumClimax += 1.0/sentenceNr;
                    }
                    else {
                        //mention = http://www.newsreader-project.eu/data/2008/07/03/6479113.xml#char=359
                        // if the sentence is not part of the mention than we take the char value
                        idx = mention.indexOf("char=");
                        if (idx >-1) {
                            idx = mention.lastIndexOf("=");
                            int sentenceNr = Integer.parseInt(mention.substring(idx+1));
                            if (sentenceNr<earliestEventMention || earliestEventMention==-1) {
                                earliestEventMention = sentenceNr;
                                jsonObject.put("sentence", mention.substring(idx + 1));
                            }
                            sumClimax += 1.0/sentenceNr;
                        }
                        // System.out.println("mention = " + mention);
                    }
                }
                if (sumClimax>maxClimax) {
                    maxClimax = sumClimax;
                }
                //    System.out.println("sumClimax = " + sumClimax);
                jsonObject.put("climax", sumClimax);
            } catch (JSONException e) {
                //   e.printStackTrace();
            }
        }
        // System.out.println("maxClimax = " + maxClimax);
        /// next we normalize the climax values and store it in the tree
        for (int i = 0; i < jsonObjects.size(); i++) {
            JSONObject jsonObject = jsonObjects.get(i);
            try {
                Double climax = Double.parseDouble(jsonObject.get("climax").toString());
                // Double proportion = Math.log(climax/maxClimax);
                Double proportion = climax/maxClimax;

                Integer climaxInteger = new Integer ((int)(100*proportion));
                if (climaxInteger>=climaxThreshold) {
                    //     System.out.println("climaxInteger = " + climaxInteger);
                    jsonObject.put("climax", climaxInteger);
                    climaxObjects.add(jsonObject);
                }

             /*   System.out.println("jsonObject.get(\"labels\").toString() = " + jsonObject.get("labels").toString());
                System.out.println("jsonObject.get(\"climax\").toString() = " + jsonObject.get("climax").toString());
                System.out.println("\tmaxClimax = " + maxClimax);
                System.out.println("\tclimax = " + climax);
                System.out.println("\tpropertion = " + propertion);
                System.out.println("\tclimaxInteger = " + climaxInteger);*/

            } catch (JSONException e) {
                //   e.printStackTrace();
            }
        }
        return climaxObjects;
    }

    static TreeSet determineClimaxValuesFirstMentionOnly (ArrayList<JSONObject> jsonObjects) {
        //1. We determine the climax score for each individual event and return a sorted list by climax
        // We sum the inverse sentence numbers of all mentions
        TreeSet climaxObjects = new TreeSet(new climaxCompare());
        Integer maxClimax = 0;
        for (int i = 0; i < jsonObjects.size(); i++) {
            JSONObject jsonObject = jsonObjects.get(i);
            try {
                int firstMention = -1;
                JSONArray mentions = (JSONArray) jsonObject.get("mentions");
                int earliestEventMention = -1;
                for (int j = 0; j < mentions.length(); j++) {
                    String mention =  mentions.get(j).toString();
                    int idx = mention.indexOf("sentence=");
                    if (idx >-1) {
                        idx = mention.lastIndexOf("=");
                        int sentenceNr = Integer.parseInt(mention.substring(idx+1));
                        if (sentenceNr<earliestEventMention || earliestEventMention==-1) {
                            earliestEventMention = sentenceNr;
                            jsonObject.put("sentence", mention.substring(idx + 1));
                            if (sentenceNr < firstMention || firstMention == -1) {
                                firstMention = sentenceNr;
                            }
                        }
                    }
                }

                /// we have the first mention for an event
                /// we calculate the climax in combination with the nr. of mentions
                /// calculate the climax score and save it
                Integer climax = 1+(1/firstMention)*mentions.length();
                jsonObject.put("climax", climax);
                if (climax>maxClimax) {
                    maxClimax = climax;
                }
                climaxObjects.add(jsonObject);

            } catch (JSONException e) {
                //   e.printStackTrace();
            }
        }
        return climaxObjects;
    }


    public static int countMentions(ArrayList<JSONObject> objects) {
        int nMentions = 0;
        for (int i = 0; i < objects.size(); i++) {
            JSONObject jsonObject = objects.get(i);
            try {
                JSONArray mentions = jsonObject.getJSONArray("mentions");
                nMentions+=mentions.length();
            } catch (JSONException e) {
              //  e.printStackTrace();
            }
        }
        return nMentions;
    }

    public static int countGroups(ArrayList<JSONObject> events) {
        ArrayList<String> groups = new ArrayList<String>();
        for (int i = 0; i < events.size(); i++) {
            JSONObject jsonObject = events.get(i);
            try {
                String groupValue = jsonObject.get("group").toString();
               // System.out.println("groupValue = " + groupValue);
                if (!groups.contains(groupValue)) {
                    groups.add(groupValue);
                }
            } catch (JSONException e) {
             //   e.printStackTrace();
            }
        }
        return groups.size();
    }


    public static int countCited(ArrayList<JSONObject> events) {
        ArrayList<String> citedNames = new ArrayList<String>();
        for (int i = 0; i < events.size(); i++) {
            JSONObject oEvent = events.get(i);
            JSONObject mentions = null;
            try {
                mentions = oEvent.getJSONObject("mentions");
                Iterator oKeys = mentions.sortedKeys();
                while (oKeys.hasNext()) {
                    String oKey = oKeys.next().toString();
                    try {
                        JSONArray actors = mentions.getJSONArray(oKey);
                        for (int j = 0; j < actors.length(); j++) {
                            String nextActor = actors.getString(j);
                            nextActor = nextActor.substring(nextActor.lastIndexOf("/") + 1);
                            if (nextActor.isEmpty()) {
                                //  System.out.println("nextActor = " + nextActor);
                            }
                            if (!citedNames.contains(nextActor)) {
                                citedNames.add(nextActor);
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return citedNames.size();
    }

    public static int countActors(ArrayList<JSONObject> events) {
        ArrayList<String> actorNames = new ArrayList<String>();
        for (int i = 0; i < events.size(); i++) {
            JSONObject oEvent = events.get(i);
            JSONObject oActorObject = null;
            try {
                oActorObject = oEvent.getJSONObject("actors");
                if (oActorObject.length()==0) {
                    System.out.println("oEvent = " + oEvent.get("instance"));
                }
                Iterator oKeys = oActorObject.sortedKeys();
                while (oKeys.hasNext()) {
                    String oKey = oKeys.next().toString();
                    try {
                        JSONArray actors = oActorObject.getJSONArray(oKey);
                        for (int j = 0; j < actors.length(); j++) {
                            String nextActor = actors.getString(j);
                            nextActor = nextActor.substring(nextActor.lastIndexOf("/") + 1);
                            if (nextActor.isEmpty()) {
                              //  System.out.println("nextActor = " + nextActor);
                            }
                            if (!actorNames.contains(nextActor)) {
                                actorNames.add(nextActor);
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            } catch (JSONException e) {
               //  e.printStackTrace();
            }
        }
        return actorNames.size();
    }


    public static void minimalizeActors(ArrayList<JSONObject> events) {
        for (int i = 0; i < events.size(); i++) {
            JSONObject oEvent = events.get(i);
            ArrayList<String> actorNames = new ArrayList<String>();
            JSONObject nActorObject = new JSONObject();
            JSONObject oActorObject = null;
            try {
                oActorObject = oEvent.getJSONObject("actors");
                Iterator oKeys = oActorObject.sortedKeys();
                while (oKeys.hasNext()) {
                    String oKey = oKeys.next().toString();
                    //System.err.println("oKey" + oKey);
                    try {
                        JSONArray actors = oActorObject.getJSONArray(oKey);
                        for (int j = 0; j < actors.length(); j++) {
                            String nextActor = actors.getString(j);
                            //System.err.println("nextActor = " + nextActor);
                            if (!actorNames.contains(nextActor)) {
                                nActorObject.append("actor:", nextActor);
                                actorNames.add(nextActor);
                            }
                        }
                    } catch (JSONException e) {
                      //  e.printStackTrace();
                    }
                }
            } catch (JSONException e) {
               //  e.printStackTrace();
            }
            if (nActorObject.length()>0) {
                oEvent.remove("actors");
                try {
                    // System.out.println("oActorObject.toString() = " + oActorObject.toString());
                    // System.out.println("nActorObject.toString() = " + nActorObject.toString());
                    oEvent.put("actors", nActorObject);
                } catch (JSONException e) {
                 //   e.printStackTrace();
                }
            }
        }
    }
    
    public static void selectActors(ArrayList<JSONObject> events, String roles) {
        String [] roleNameSpaces = roles.split(";");
        for (int i = 0; i < events.size(); i++) {
            JSONObject oEvent = events.get(i);
            JSONObject nActorObject = new JSONObject();
            JSONObject oActorObject = null;
            try {
                oActorObject = oEvent.getJSONObject("actors");
                Iterator oKeys = oActorObject.sortedKeys();
                while (oKeys.hasNext()) {
                    String oKey = oKeys.next().toString();
                    for (int r = 0; r < roleNameSpaces.length; r++) {
                        String roleNameSpace = roleNameSpaces[r];
                        if (oKey.startsWith(roleNameSpace)) {
                            try {
                                JSONArray actors = oActorObject.getJSONArray(oKey);
                                for (int j = 0; j < actors.length(); j++) {
                                    String nextActor = actors.getString(j);
                                    nActorObject.append(oKey, nextActor);
                                }
                            } catch (JSONException e) {
                                //  e.printStackTrace();
                            }
                        }
                    }
                }
            } catch (JSONException e) {
               //  e.printStackTrace();
            }
            if (nActorObject.length()>0) {
                oEvent.remove("actors");
                try {
                    // System.out.println("oActorObject.toString() = " + oActorObject.toString());
                    // System.out.println("nActorObject.toString() = " + nActorObject.toString());
                    oEvent.put("actors", nActorObject);
                } catch (JSONException e) {
                 //   e.printStackTrace();
                }
            }
        }
    }

    public static HashMap <String, Integer> createActorCount (ArrayList<JSONObject> events) {
        HashMap <String, Integer> actorCount = new HashMap<String, Integer>();
        for (int i = 0; i < events.size(); i++) {
            JSONObject oEvent = events.get(i);
            JSONObject oActorObject = null;
            try {
                oActorObject = oEvent.getJSONObject("actors");
                Iterator oKeys = oActorObject.sortedKeys();
                while (oKeys.hasNext()) {
                    String oKey = oKeys.next().toString();
                    try {
                        JSONArray actors = oActorObject.getJSONArray(oKey);
                        for (int j = 0; j < actors.length(); j++) {
                            String nextActor = actors.getString(j);
                            nextActor = nextActor.substring(nextActor.lastIndexOf("/") + 1);
                            if (actorCount.containsKey(nextActor)) {
                                Integer cnt = actorCount.get(nextActor);
                                cnt++;
                                actorCount.put(nextActor, cnt);
                            }
                            else {
                                actorCount.put(nextActor,1);
                            }
                        }
                    } catch (JSONException e) {
                      //  e.printStackTrace();
                    }
                }
            } catch (JSONException e) {
                // e.printStackTrace();
            }
        }
        return actorCount;
    }

    static void count (HashMap<String, Integer> count, String topic) {
         if (count.containsKey(topic)) {
             Integer cnt = count.get(topic);
             cnt++;
             count.put(topic, cnt);
         }
        else {
             count.put(topic, 1);
         }
    }

    static double informationValue (HashMap<String, Integer> count, String topic, int cnt) {
        double score = 0;
        if (count.containsKey(topic)) {
            Integer tot = count.get(topic);
            score = Math.abs(Math.log(cnt/tot));
        }
        return score;
    }

    public static void renameStories(ArrayList<JSONObject> jsonObjects, EuroVoc euroVoc, EuroVoc eurovocBlackList) {
        HashMap<String, String> renameMap = new HashMap<String, String>();
        HashMap<String, Integer> totalTopicCount = new HashMap<String, Integer>();
        HashMap<String, ArrayList<String>> storyTopicMap = new HashMap<String, ArrayList<String>>();
        for (int i = 0; i < jsonObjects.size(); i++) {
            JSONObject jsonObject = jsonObjects.get(i);
            try {
                JSONArray topics = null;
                try {
                    topics = jsonObject.getJSONArray("topics");
                } catch (JSONException e) {
                   // e.printStackTrace();
                }
                if (topics!=null) {
                    String groupName = jsonObject.getString("groupName");
                    if (storyTopicMap.containsKey(groupName)) {
                        ArrayList<String> givenTopics = storyTopicMap.get(groupName);
                        for (int j = 0; j < topics.length(); j++) {
                            String topic = topics.get(j).toString();
                            if (eurovocBlackList==null) {
                                count(totalTopicCount, topic);
                                givenTopics.add(topic);
                            }
                            else if (!eurovocBlackList.startsWith(topic)&&
                                     !eurovocBlackList.uriLabelMap.containsKey(topic)) {
                                    count(totalTopicCount, topic);
                                    givenTopics.add(topic);
                                } else {
                                 //   System.out.println("blacklist topic = " + topic);
                            }
                        }
                        storyTopicMap.put(groupName, givenTopics);
                    } else {
                        ArrayList<String> givenTopics = new ArrayList<String>();
                        for (int j = 0; j < topics.length(); j++) {
                            String topic = topics.get(j).toString();
                            if (eurovocBlackList==null) {
                                count(totalTopicCount, topic);
                                givenTopics.add(topic);
                            }
                            else if (!eurovocBlackList.startsWith(topic)&&
                                    !eurovocBlackList.uriLabelMap.containsKey(topic)) {
                                count(totalTopicCount, topic);
                                givenTopics.add(topic);
                            } else {
                                //   System.out.println("blacklist topic = " + topic);
                            }
                        }
                        storyTopicMap.put(groupName, givenTopics);
                    }
                   // System.out.println("groupName = " + groupName);
                }
            } catch (JSONException e) {
              //  e.printStackTrace();
            }
        }
        Set keySet = storyTopicMap.keySet();
        Iterator<String> keys = keySet.iterator();
        while (keys.hasNext()) {
            String groupName = keys.next();
            double max = -1;
            String maxTopic = "";
            ArrayList<String> topics = storyTopicMap.get(groupName);
            HashMap<String, Integer> topicCount = new HashMap<String, Integer>();
            for (int i = 0; i < topics.size(); i++) {
                String topic = topics.get(i);
                if (!topic.isEmpty()) {
                    count(topicCount, topic);
                }
            }
            Set cntSet = topicCount.keySet();
            Iterator<String> cntKeys = cntSet.iterator();
            while (cntKeys.hasNext()) {
                String topic = cntKeys.next();
                if (!topic.isEmpty()) {
                    Integer cnt = topicCount.get(topic);
                    double score = informationValue(totalTopicCount, topic, cnt);
                    if (score > max) {
                        max = score;
                        maxTopic = topic;
                    }
                }
            }
            if (!maxTopic.isEmpty()) {
                // System.out.println("maxTopic = " + maxTopic);
                if (euroVoc.uriLabelMap.containsKey(maxTopic)) {
                    String label = euroVoc.uriLabelMap.get(maxTopic);
                    //System.out.println("label = " + label);
                    renameMap.put(groupName, label);
                } else {
                    //  System.out.println("could not find maxTopic = " + maxTopic);
                    String label = maxTopic.replace("+", " ");
                    int idx = label.lastIndexOf("/");
                    if (idx>-1) {
                        label = label.substring(idx+1);
                    }
                    renameMap.put(groupName, label);
                }
            }
        }
        HashMap<String, Integer> topicScore = new HashMap<String, Integer>();
        for (int i = 0; i < jsonObjects.size(); i++) {
            JSONObject jsonObject = jsonObjects.get(i);
            String group = null;
            String groupName = null;
            String groupScore = null;
            try {
                groupName = jsonObject.getString("groupName");
                groupScore = jsonObject.getString("groupScore");
                if (renameMap.containsKey(groupName)) {
                    String label = renameMap.get(groupName);
                   // String topicGroup = groupScore+":"+"["+label+"]"+groupName;
                   // String topicGroupName = "["+label+"]"+groupName;
                    String topicGroup = groupScore+":"+"["+label+"]";
                    String topicGroupName = "["+label+"]";
                   // System.out.println("topicGroupName = " + topicGroupName);
                    jsonObject.put("group", topicGroup);
                    jsonObject.put("groupName", topicGroupName);

                    Integer currentScore = Integer.parseInt(groupScore);
                    if (topicScore.containsKey(topicGroupName)) {
                        Integer score = topicScore.get(topicGroupName);
                        if (currentScore>score) {
                            topicScore.put(topicGroupName,currentScore);
                        }
                    }
                    else {
                        topicScore.put(topicGroupName,currentScore);
                    }
                }
                else {
                 //   System.out.println("groupName did not got a topic to rename = " + groupName);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        for (int i = 0; i < jsonObjects.size(); i++) {
            JSONObject jsonObject = jsonObjects.get(i);
            try {
                String groupName = jsonObject.getString("groupName");
                if (topicScore.containsKey(groupName)) {
                    Integer score = topicScore.get(groupName);
                    String scoreString = score.toString();
                    if (scoreString.length()==1) scoreString = "00"+scoreString;
                    if (scoreString.length()==2) scoreString = "0"+scoreString;
                    String topicGroup = scoreString+":"+groupName;
                    jsonObject.put("group", topicGroup);
                    jsonObject.put("groupScore", scoreString);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }


    static public class climaxCompare implements Comparator {
        public int compare (Object aa, Object bb) {
            try {
                Integer a = Integer.parseInt(((JSONObject) aa).get("climax").toString());
                Integer b = Integer.parseInt(((JSONObject)bb).get("climax").toString());
                if (a <= b) {
                    return 1;
                }
                else {
                    return -1;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return -1;
        }
    }


    static ArrayList<JSONObject> createGroupsForJSONArrayList (ArrayList<JSONObject> jsonObjects) {
        ArrayList<JSONObject> groupedObjects = new ArrayList<JSONObject>();
        HashMap<String, ArrayList<JSONObject>> frameMap = new HashMap<String, ArrayList<JSONObject>>();
        for (int i = 0; i < jsonObjects.size(); i++) {
            JSONObject jsonObject = jsonObjects.get(i);
            try {
                //JSONArray superFrames = (JSONArray) jsonObject.get("fnsuperframes");
                JSONArray superFrames = (JSONArray) jsonObject.get("esosuperclasses");
                for (int j = 0; j < superFrames.length(); j++) {
                    String frame = (String) superFrames.get(j);
                    if (frameMap.containsKey(frame)) {
                        ArrayList<JSONObject> objects = frameMap.get(frame);
                        objects.add(jsonObject);
                        frameMap.put(frame, objects);
                    }
                    else {
                        ArrayList<JSONObject> objects = new ArrayList<JSONObject>();
                        objects.add(jsonObject);
                        frameMap.put(frame, objects);
                    }
                }
            } catch (JSONException e) {
                //  e.printStackTrace();
                //JSONArray frames = (JSONArray)jsonObject.get("frames");

                if (frameMap.containsKey("noframe")) {
                    ArrayList<JSONObject> objects = frameMap.get("noframe");
                    objects.add(jsonObject);
                    frameMap.put("noframe", objects);
                }
                else {
                    ArrayList<JSONObject> objects = new ArrayList<JSONObject>();
                    objects.add(jsonObject);
                    frameMap.put("noframe", objects);
                }
            }
        }
        SortedSet<PhraseCount> list = new TreeSet<PhraseCount>(new PhraseCount.Compare());
        Set keySet = frameMap.keySet();
        Iterator<String> keys = keySet.iterator();
        while (keys.hasNext()) {
            String key = keys.next();
            ArrayList<JSONObject> objects = frameMap.get(key);
            PhraseCount pcount = new PhraseCount(key, objects.size());
            list.add(pcount);
        }
        for (PhraseCount pcount : list) {
            ArrayList<JSONObject> allObjects = frameMap.get(pcount.getPhrase());
            int firstMention = -1;
            Vector<Integer> climaxIndex = new Vector<Integer>();
            /// filter out objects already covered from the group
            ArrayList<JSONObject> objects = new ArrayList<JSONObject>();
            for (int i = 0; i < allObjects.size(); i++) {
                JSONObject object = allObjects.get(i);
                if (!groupedObjects.contains(object)) {
                    objects.add(object);
                }
            }
            for (int i = 0; i < objects.size(); i++) {
                JSONObject jsonObject = objects.get(i);
                try {
                    JSONArray mentions = (JSONArray) jsonObject.get("mentions");
                    int earliestEventMention = -1;
                    for (int j = 0; j < mentions.length(); j++) {
                        String mention =  mentions.get(j).toString();
                        int idx = mention.indexOf("sentence=");
                        if (idx >-1) {
                            idx = mention.lastIndexOf("=");
                            int sentenceNr = Integer.parseInt(mention.substring(idx+1));
                            if (sentenceNr<earliestEventMention || earliestEventMention==-1) {
                                earliestEventMention = sentenceNr;
                                jsonObject.put("sentence", mention.substring(idx + 1));
                                if (sentenceNr < firstMention || firstMention == -1) {
                                    firstMention = sentenceNr;
                                }
                            }
                        }
                    }
                    if (!climaxIndex.contains(earliestEventMention)) {
                        climaxIndex.add(earliestEventMention);
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            Collections.sort(climaxIndex);
/*
            for (int i = 0; i < climaxIndex.size(); i++) {
                Integer integer = climaxIndex.get(i);
                System.out.println("integer = " + integer);
            }
*/
            for (int i = 0; i < objects.size(); i++) {
                JSONObject jsonObject = objects.get(i);
                try {
                    // JSONObject sentenceObject = (JSONObject) jsonObject.get("sentence");
                    int sentenceNr = Integer.parseInt((String) jsonObject.get("sentence"));
                    // Integer climax = sentenceNr-firstMention;
                    Integer climax = 1+climaxIndex.size()-climaxIndex.indexOf(sentenceNr);
                    Float size = 1+Float.valueOf(((float)((5*climaxIndex.size()-5*climaxIndex.indexOf(sentenceNr))/(float)climaxIndex.size())));
                    //    System.out.println("climax.toString() = " + climax.toString());
                    //    System.out.println("size.toString() = " + size.toString());

/*
                    String combinedKey = pcount.getPhrase()+"."+climax.toString();
                    jsonObject.put("climax", combinedKey);*/
                    jsonObject.put("climax", climax.toString());
                    jsonObject.put("group", pcount.getPhrase());
                    if (!groupedObjects.contains(jsonObject)) {
                        groupedObjects.add(jsonObject);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return groupedObjects;
    }
    static boolean hasObject (ArrayList<JSONObject> objects, JSONObject object) {
        for (int i = 0; i < objects.size(); i++) {
            JSONObject jsonObject = objects.get(i);
            try {
                // System.out.println("jsonObject.get(\"instance\").toString() = " + jsonObject.get("instance").toString());

                if (jsonObject.get("instance").toString().equals(object.get("instance").toString())) {
                    return true;
                }
            } catch (JSONException e) {
                //  e.printStackTrace();
            }
        }
        return false;
    }

    static String getActorByRoleFromEvent (JSONObject event, String role) throws JSONException {
        String actor = "";
        JSONObject actorObject = event.getJSONObject("actors");
        Iterator keys = actorObject.sortedKeys();
        while (keys.hasNext()) {
            String key = keys.next().toString(); //role
            //  System.out.println("key = " + key);
            if (key.equalsIgnoreCase(role)) {
                JSONArray actors = actorObject.getJSONArray(key);
                for (int j = 0; j < actors.length(); j++) {
                    String nextActor = actors.getString(j);
                    nextActor = nextActor.substring(nextActor.lastIndexOf("/")+1);
                    if (actor.indexOf(nextActor)==-1) {
                        actor += ":" +nextActor;
                    }
                    //break;
                }
            }
        }
        return actor;
    }

    static ArrayList<String> getActorsByRoleFromEvent (JSONObject event, String role) throws JSONException {
        ArrayList<String> actorList = new ArrayList<String>();
        JSONObject actorObject = event.getJSONObject("actors");
        Iterator keys = actorObject.sortedKeys();
        while (keys.hasNext()) {
            String key = keys.next().toString(); //role
            //  System.out.println("key = " + key);
            if (key.equalsIgnoreCase(role)) {
                JSONArray actors = actorObject.getJSONArray(key);
                for (int j = 0; j < actors.length(); j++) {
                    String nextActor = actors.getString(j);
                    nextActor = nextActor.substring(nextActor.lastIndexOf("/")+1);
                    if (!actorList.contains(nextActor)) {
                        actorList.add(nextActor);
                    }
                }
            }
        }
        return actorList;
    }

    static String getfirstActorByRoleFromEvent (JSONObject event, String role) throws JSONException {
        String actor = "";
        try {
            JSONObject actorObject = event.getJSONObject("actors");
            Iterator keys = actorObject.sortedKeys();
            while (keys.hasNext()) {
                String key = keys.next().toString(); //role
                //  System.out.println("key = " + key);
                if (key.equalsIgnoreCase(role)) {
                    JSONArray actors = actorObject.getJSONArray(key);
                    for (int j = 0; j < actors.length(); j++) {
                        String nextActor = actors.getString(j);
                        nextActor = nextActor.substring(nextActor.lastIndexOf("/")+1);
                        actor += ":" + nextActor;
                        break;
                    }
                }
            }
        } catch (JSONException e) {
           // e.printStackTrace();
        }
        return actor;
    }

    static String getActorFromEvent (JSONObject event, String actorString) throws JSONException {
        String actor = "";
        JSONObject actorObject = event.getJSONObject("actors");
        Iterator keys = actorObject.sortedKeys();
        while (keys.hasNext()) {
            String key = keys.next().toString(); //role
            //  System.out.println("key = " + key);
            JSONArray actors = actorObject.getJSONArray(key);
            for (int j = 0; j < actors.length(); j++) {
                String nextActor = actors.getString(j);
                nextActor = nextActor.substring(nextActor.lastIndexOf("/")+1);
                if (nextActor.indexOf(actorString)>-1) {
                    if (actor.indexOf(nextActor)==-1) {
                        actor += ":" +nextActor;
                    }
                }
            }
        }
        return actor;
    }

    static boolean hasActorInEvent (JSONObject event, ArrayList<String> actorList) throws JSONException {
        JSONObject actorObject = event.getJSONObject("actors");
        Iterator keys = actorObject.sortedKeys();
        while (keys.hasNext()) {
            String key = keys.next().toString(); //role
            //  System.out.println("key = " + key);
            JSONArray actors = actorObject.getJSONArray(key);
            for (int j = 0; j < actors.length(); j++) {
                String nextActor = actors.getString(j);
                nextActor = nextActor.substring(nextActor.lastIndexOf("/") + 1);
                if (actorList.contains(nextActor)) {
                    return true;
                }
            }
        }
        return false;
    }

    static ArrayList<JSONObject> intersectEventObjects(ArrayList<JSONObject> set1, ArrayList<JSONObject> set2) throws JSONException {
        ArrayList<JSONObject> intersection = new ArrayList<JSONObject>();
        for (int i = 0; i < set1.size(); i++) {
            JSONObject object1 = set1.get(i);
            for (int j = 0; j < set2.size(); j++) {
                JSONObject object2 = set2.get(j);
                if (object1.get("instance").toString().equals(object2.get("instance").toString())) {
                    intersection.add(object1);
                }
            }
        }
        return  intersection;
    }
    static ArrayList<JSONObject> mergeEventObjects(ArrayList<JSONObject> set1, ArrayList<JSONObject> set2) throws JSONException {
        ArrayList<JSONObject> merge = set2;
        for (int i = 0; i < set1.size(); i++) {
            JSONObject object1 = set1.get(i);
            boolean has = false;
            for (int j = 0; j < merge.size(); j++) {
                JSONObject object2 = merge.get(j);
                if (object1.get("instance").toString().equals(object2.get("instance").toString())) {
                    has = true;
                    break;
                }
            }
            merge.add(object1);
        }
        return  merge;
    }


    static void addObjectToGroup (ArrayList<JSONObject> groupObjects,
                                  String group,
                                  String groupName,
                                  String groupScore,
                                  JSONObject object,
                                  int divide,
                                  int climaxThreshold) throws JSONException {
        Float size = null;
        object.put("group", group);
        object.put("groupName", groupName);
        object.put("groupScore", groupScore);
        Double climax = 0.0;
        try {
            climax = Double.parseDouble(object.get("climax").toString());
        } catch (NumberFormatException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (climax >= climaxThreshold) {
            // size = 5 * Float.valueOf((float) (1.0 * climax / groupClimax));
            groupObjects.add(object);
        }
    }


   static String climaxString (Integer climax) {
        String str = "";
        if (climax>=100) {
            str  = climax.toString();
        }
        else if (climax>9){
            str = "0"+climax.toString();
        }
        else if (climax>0){
            str = "00"+climax.toString();
        }
        else {
            str = "000";
        }
        return str;
    }


    
    static ArrayList<JSONObject> mergeEvents (ArrayList<JSONObject> inputEvents,
                                              String timeGran,
                                              String actionOnt,
                                              int actionSim) throws JSONException {
        ArrayList<JSONObject> mergedEvents = new ArrayList<JSONObject>();
        HashMap<String, ArrayList<JSONObject>> eventMap = new HashMap<String, ArrayList<JSONObject>>();
        for (int i = 0; i < inputEvents.size(); i++) {
            JSONObject event =  inputEvents.get(i);
            try {
                String time = event.getString("time");
                if (time.length()>=4) {
                    if (timeGran.equalsIgnoreCase("Y")) {
                        time = time.substring(0, 3);
                    } else if (timeGran.equalsIgnoreCase("M")) {
                        time = time.substring(0, 5);
                    } else if (timeGran.equalsIgnoreCase("W")) {
                        if (time.length()<7)  {
                            time = time.substring(0, 5);
                            time += "w0";
                        }
                        else {
                            Integer d = Integer.parseInt(time.substring(6));
                            time = time.substring(0, 5);
                            if (d < 8) time += "w1";
                            else if (d < 15) time += "w2";
                            else if (d < 22) time += "w3";
                            else time += "w4";
                        }
                    } else if (timeGran.equalsIgnoreCase("N")) {
                        time = "anytime";
                    }
                    if (eventMap.containsKey(time)) {
                        ArrayList<JSONObject> ev = eventMap.get(time);
                        ev.add(event);
                        eventMap.put(time, ev);
                    } else {
                        ArrayList<JSONObject> ev = new ArrayList<JSONObject>();
                        ev.add(event);
                        eventMap.put(time, ev);
                    }
                }
            } catch (JSONException e) {
              //  e.printStackTrace();
            }
        }
        Set keySet = eventMap.keySet();
        Iterator<String> keys = keySet.iterator();
        while (keys.hasNext()) {
            String key = keys.next();
            ArrayList<JSONObject> events = eventMap.get(key);
           // String instance = key+":"+events.size();
            if (!actionOnt.equalsIgnoreCase("N")) {
                ArrayList<JSONObject> mergers = null;
                try {
                    mergers = mergeEventArrayRecursive(events, actionOnt, actionSim);
                } catch (JSONException e) {
                    //  e.printStackTrace();
                }
                if (mergers != null) {
                    for (int i = 0; i < mergers.size(); i++) {
                        JSONObject jsonObject = mergers.get(i);
                        mergedEvents.add(jsonObject);
                    }
                }
            }
            else {
                // we marged all the events
                JSONObject merged = mergeEventArray(events);
                mergedEvents.add(merged);
            }
        }
        return mergedEvents;
    }

    static JSONObject mergeEventArray (ArrayList<JSONObject> events) throws JSONException {
        JSONObject firstEvent = events.get(0);
        ArrayList<JSONObject> remainingEvents = new ArrayList<JSONObject>();
        for (int i = 1; i < events.size(); i++) {
            JSONObject jsonObject = events.get(i);
            remainingEvents.add(jsonObject);
        }
        mergeEventArrayWithEvent(remainingEvents, firstEvent);
       return firstEvent;
    }

    static ArrayList<JSONObject> mergeEventArrayRecursive (ArrayList<JSONObject> events, String ont, int sim) throws JSONException {
        ArrayList<JSONObject> finalEvents = new ArrayList<JSONObject>();
        ArrayList<String> mergedEvents = new ArrayList<String>();
        for (int i = 0; i < events.size(); i++) {
            JSONObject event1 = events.get(i);
            String eventId1 = event1.getString("instance");
            if (!mergedEvents.contains(eventId1)) {
                mergedEvents.add(eventId1);
                ArrayList<JSONObject> event1MatchEvents = new ArrayList<JSONObject>();
                JSONObject classData1 = null;
                try {
                    classData1 = event1.getJSONObject("classes");
                } catch (JSONException e) {
                   // e.printStackTrace();
                }
                if (classData1!=null) {
                    for (int j = i = 1; j < events.size(); j++) {
                        JSONObject event2 = events.get(j);
                        String eventId2 = event2.getString("instance");
                        if (!mergedEvents.contains(eventId2)) {
                            int nMatches = 0;
                            JSONObject classData2 = event2.getJSONObject("classes");
                            JSONArray types1 = null;
                            JSONArray types2 = null;
                            if (ont.equalsIgnoreCase("any")) {
                                Iterator keys = classData1.keys();
                                while (keys.hasNext()) {
                                    String key = keys.next().toString();
                                    JSONArray concepts1 = classData1.getJSONArray(key);
                                    JSONArray concepts2 = classData2.getJSONArray(key);
                                    for (int c = 0; c < concepts1.length(); c++) {
                                        String concept = concepts1.getString(c);
                                        if (concepts2.toString().indexOf(concept)>-1) {
                                            // match
                                            nMatches++;
                                        }
                                    }
                                    int prop1 = (100*nMatches)/concepts1.length();
                                    int prop2 = (100*nMatches)/concepts2.length();
                                    if (prop1>=sim & prop2>=sim) {
                                        event1MatchEvents.add(event2);
                                        mergedEvents.add(eventId2);
                                        nMergedEvents++;
                                        break;
                                    }
                                }
                            }
                            else {
                                String [] ontFields = ont.split(";");
                                for (int l = 0; l < ontFields.length; l++) {
                                    String ontField = ontFields[l];
                                    try {
                                        types1 = classData1.getJSONArray(ontField);
                                        types2 = classData2.getJSONArray(ontField);
                                    } catch (JSONException e) {
                                        // e.printStackTrace();
                                    }
                                    if (types1!=null && types2!=null) {
                                        for (int k = 0; k < types1.length(); k++) {
                                            String t1 = (String) types1.get(k);
                                            if (types2.toString().indexOf(t1) > -1) {
                                                // match
                                                nMatches++;
                                            }

                                        }
                                        int prop1 = (100*nMatches)/types1.length();
                                        int prop2 = (100*nMatches)/types2.length();
                                        if (prop1>=sim & prop2>=sim) {
                                            event1MatchEvents.add(event2);
                                            mergedEvents.add(eventId2);
                                            nMergedEvents++;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (event1MatchEvents.size()>0) {
                    mergeEventArrayWithEvent(event1MatchEvents, event1);
                }
                finalEvents.add(event1);
            }
        }
        return finalEvents;
    }


    static ArrayList<JSONObject> mergeEventArrayRecursive (ArrayList<JSONObject> events, String ont) throws JSONException {
        ArrayList<JSONObject> finalEvents = new ArrayList<JSONObject>();
        ArrayList<String> mergedEvents = new ArrayList<String>();
        for (int i = 0; i < events.size(); i++) {
            JSONObject event1 = events.get(i);
            String eventId1 = event1.getString("instance");
            if (!mergedEvents.contains(eventId1)) {
                mergedEvents.add(eventId1);
                ArrayList<JSONObject> event1MatchEvents = new ArrayList<JSONObject>();
                JSONObject classData1 = null;
                try {
                    classData1 = event1.getJSONObject("classes");
                } catch (JSONException e) {
                   // e.printStackTrace();
                }
                if (classData1!=null) {
                    for (int j = i = 1; j < events.size(); j++) {
                        JSONObject event2 = events.get(j);
                        String eventId2 = event2.getString("instance");
                        if (!mergedEvents.contains(eventId2)) {
                            JSONObject classData2 = event1.getJSONObject("classes");
                            JSONArray types1 = null;
                            JSONArray types2 = null;
                            if (ont.equalsIgnoreCase("any")) {
                                Iterator keys = classData1.keys();
                                while (keys.hasNext()) {
                                    String key = keys.next().toString();
                                    JSONArray concepts = classData1.getJSONArray(key);
                                    for (int c = 0; c < concepts.length(); c++) {
                                        String concept = concepts.getString(c);
                                        Iterator keys2 = classData2.keys();
                                        while (keys2.hasNext()) {
                                            String key2 = keys2.next().toString(); //role
                                            JSONArray concepts2 = classData2.getJSONArray(key2);
                                            if (concepts2.toString().indexOf(concept)>-1) {
                                                // match
                                                event1MatchEvents.add(event2);
                                                mergedEvents.add(eventId2);
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                            else {
                                String [] ontFields = ont.split(";");
                                for (int l = 0; l < ontFields.length; l++) {
                                    String ontField = ontFields[l];
                                    try {
                                        types1 = classData1.getJSONArray(ontField);
                                        types2 = classData2.getJSONArray(ontField);
                                    } catch (JSONException e) {
                                        // e.printStackTrace();
                                    }
                                    if (types1!=null && types2!=null) {
                                        for (int k = 0; k < types1.length(); k++) {
                                            String t1 = (String) types2.get(k);
                                            if (types2.toString().indexOf(t1) > -1) {
                                                // match
                                                event1MatchEvents.add(event2);
                                                mergedEvents.add(eventId2);
                                                break;
                                            }

                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (event1MatchEvents.size()>0) {
                    mergeEventArrayWithEvent(event1MatchEvents, event1);
                }
                finalEvents.add(event1);
            }
        }
        return finalEvents;
    }

    static void mergeEventArrayWithEvent (ArrayList<JSONObject> events, JSONObject mEvent) throws JSONException {
        ArrayList<String> times = new ArrayList<String>();
        String time =  (String) mEvent.get("time");
        times.add(time);
        ArrayList<String> actors = new ArrayList<String>();
        JSONObject mActors = mEvent.getJSONObject("actors");
        Iterator keys = mActors.sortedKeys();
        while (keys.hasNext()) {
            String key = keys.next().toString(); //role
            JSONArray actorObjects = mActors.getJSONArray(key);
            actorObjects.toString();
            for (int j = 0; j < actorObjects.length(); j++) {
                String nextActor = actorObjects.getString(j);
                actors.add(nextActor);
            }
        }

        JSONArray mLabels = mEvent.getJSONArray("labels");
        JSONArray mTopics = mEvent.getJSONArray("topics");

        for (int i = 0; i < events.size(); i++) {
            // System.out.println("i = " + i);
            JSONObject event = events.get(i);
            time =  (String) event.get("time");
            if (!times.contains(time)) {
                times.add(time);
            }
            JSONObject actorObject = event.getJSONObject("actors");
            keys = actorObject.sortedKeys();
            while (keys.hasNext()) {
                String key = keys.next().toString(); //role
                JSONArray otherActors = actorObject.getJSONArray(key);
                for (int j = 0; j < otherActors.length(); j++) {
                    String nextActor = otherActors.getString(j);
                    if (!actors.contains(nextActor)) {
                        mActors.append(key, nextActor);
                    }
                }
            }
            mEvent.put("actors", mActors);

            try {
                JSONArray oLabels = event.getJSONArray("labels");
                for (int j = 0; j < oLabels.length(); j++) {
                    String s =  (String) oLabels.get(j);
                    if (mLabels.toString().indexOf(s)==-1) {
                        mEvent.append("labels", s);
                    }
                }
                mEvent.put("labels", mLabels);
            } catch (JSONException e) {
               // e.printStackTrace();
            }

            try {
                JSONArray oTopics = event.getJSONArray("topics");
                for (int j = 0; j < oTopics.length(); j++) {
                    String s =  (String) oTopics.get(j);
                    if (mTopics.toString().indexOf(s)==-1) {
                        mEvent.append("topics", s);
                    }
                }
                mEvent.put("topics", mTopics);
            } catch (JSONException e) {
              //  e.printStackTrace();
            }

            JSONArray mMentions = null;
            try {
                mMentions = (JSONArray) mEvent.get("mentions");
            } catch (JSONException e) {
            }
            JSONArray mentions = (JSONArray) event.get("mentions");
            if (mMentions==null) {
                mEvent.put("mentions", mentions);
            }
            else {
                for (int m = 0; m < mentions.length(); m++) {
                    JSONObject mentionObject = (JSONObject) mentions.get(m);
                    mEvent.append("mentions", mentionObject);
                }
            }
        }
        Collections.sort(times);
        //System.out.println("times.toString() = " + times.toString());
        mEvent.put("time", times.get(0));
        for (int i = 0; i < times.size(); i++) {
            String t = times.get(i);
            mEvent.append("period", t);
        }
    }

    @Deprecated
    public static PerspectiveJsonObject getPerspectiveObjectForEvent(TrigTripleData trigTripleData, String mentionUri, String meta) {
        PerspectiveJsonObject perspectiveJsonObject = new PerspectiveJsonObject();
        String author = "";
        String cite = "";
       // System.out.println("Start");
        ArrayList<String> perspectives = new ArrayList<String>();
        if (trigTripleData.tripleMapGrasp.containsKey(mentionUri)) {
            ArrayList<Statement> perspectiveTriples = trigTripleData.tripleMapGrasp.get(mentionUri);
           // System.out.println("perspectiveTriples.size() = " + perspectiveTriples.size());
            for (int i = 0; i < perspectiveTriples.size(); i++) {
                Statement statement = perspectiveTriples.get(i);
                String subject = statement.getSubject().getURI();
                String predicate = statement.getPredicate().getURI();
                String object = statement.getObject().toString();
                if (predicate.endsWith("#hasAttribution")) {
                    if (trigTripleData.tripleMapGrasp.containsKey(object)) {
                        ArrayList<Statement> perspectiveValues = trigTripleData.tripleMapGrasp.get(object);
                        for (int j = 0; j < perspectiveValues.size(); j++) {
                            Statement statement1 = perspectiveValues.get(j);
                            //System.out.println("statement1.toString() = " + statement1.toString());
                           // System.out.println("statement1.getObject().toString() = " + statement1.getObject().toString());
                            //ttp://www.w3.org/ns/prov#wasAttributedTo,
                            if (statement1.getPredicate().getURI().endsWith("#wasAttributedTo")) {
                                if (trigTripleData.tripleMapGrasp.containsKey(statement1.getObject().toString())) {
                                    //// this means the source has properties so it is likely to be the document with an author
                                    ArrayList<Statement> provStatements = trigTripleData.tripleMapGrasp.get(statement1.getObject().toString());
                                    for (int k = 0; k < provStatements.size(); k++) {
                                        Statement statement2 = provStatements.get(k);
                                        author = statement2.getObject().toString();
                                        int idx = author.lastIndexOf("/");
                                        if (idx > -1) {
                                            author = author.substring(idx + 1);
                                        }
                                     //   System.out.println("author source = " + author);
                                    }
                                } else {
                                    //// it is not the document so a cited source
                                    cite = statement1.getObject().toString();
                                    int idx = cite.lastIndexOf("/");
                                    if (idx > -1) {
                                        cite = cite.substring(idx + 1);
                                    }
/*                                  THIS DOES NOT WORK: PRONOUNS, RECEPTIONIST, ETC...
                                    //// There can be source documents without meta data.
                                    //// In that case, there are no triples for in tripleMapGrasp with this subject but it is still a document
                                    //// The next hack checks for upper case characters in the URI
                                    //// If they are present, we assume it is somebody otherwise we assume it is a document and we assign it to the meta string

                                    if (cite.toLowerCase().equals(cite)) {
                                        //// no uppercase characters
                                        cite = meta;
                                    }
*/
                                  //  System.out.println("quote source = " + cite);

                                }
                            } else if (statement1.getPredicate().getURI().endsWith("#value")) {
                                String perspective = "";
                                String str = statement1.getObject().toString();
                                //   System.out.println("str = " + str);
                                int idx = str.lastIndexOf("#");
                                if (idx > -1) {
                                    perspective = str.substring(idx + 1);
                                } else {
                                    idx = str.lastIndexOf("/");
                                    if (idx > -1) {
                                        perspective = str.substring(idx + 1);
                                    } else {
                                        perspective = str;
                                    }
                                }
                                ArrayList<String> myPerspectives = PerspectiveJsonObject.normalizePerspectiveValue(perspective);
                                for (int k = 0; k < myPerspectives.size(); k++) {
                                    String myPerspective = myPerspectives.get(k);
                                    if (!perspectives.contains(myPerspective)) {
                                      //  System.out.println("myPerspective = " + myPerspective);
                                        perspectives.add(myPerspective);
                                    }
                                }
                            } else {
                               //     System.out.println("statement1.getPredicate().getURI() = " + statement1.getPredicate().getURI());
                            }
                        }
                    }
                }
            }

            if (perspectives.size() > 0) {
              //  System.out.println("final author = " + author);
                perspectiveJsonObject = new PerspectiveJsonObject(perspectives, author, cite, "", "", "", mentionUri, null);
            }
        }
        return perspectiveJsonObject;
    }

    static ArrayList<JSONObject> removePerspectiveEvents (TrigTripleData trigTripleData,
                                         ArrayList<JSONObject> targetEvents) {
        Set keySet = trigTripleData.tripleMapGrasp.keySet();
        ArrayList<JSONObject> realEvents = new ArrayList<JSONObject>();
        for (int i = 0; i < targetEvents.size(); i++) {
            JSONObject mEvent = targetEvents.get(i);
            JSONArray mMentions = null;
            try {
                mMentions = (JSONArray) mEvent.get("mentions");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (mMentions != null) {
                boolean pEvent = false;
                for (int m = 0; m < mMentions.length(); m++) {
                    try {
                        JSONObject mentionObject = (JSONObject) mMentions.get(m);
                        String uriString = mentionObject.getString("uri");
                        JSONArray offsetArray = mentionObject.getJSONArray("char");
                        String mention = JsonStoryUtil.getStringValueforMention(uriString, offsetArray);
                        System.out.println("mention = " + mention);
                        if (trigTripleData.tripleMapGrasp.containsKey(mention)) {
                            /// we remove the events from the story line!!!!!
                            pEvent=true;
                            break;
                        }
                        else {
                        }
                    } catch (JSONException e) {
                       e.printStackTrace();
                    }
                }
                if (!pEvent) {
                    realEvents.add(mEvent);
                }
            }
        }
        return realEvents;
    }

    static public void addPerspectiveToMention (JSONObject mentionObject, PerspectiveJsonObject perspectiveJsonObject) throws JSONException {
        if (perspectiveJsonObject!=null) {
            String source = perspectiveJsonObject.getCite();
            if (!source.isEmpty()) {
                if (source.toLowerCase().equals(source)) {
                    //// no uppercase characters
                    source = "cite:someone";
                }
                else {
                    source = JsonStoryUtil.normalizeSourceValue(source);
                    source = JsonStoryUtil.cleanCite(source);
                    source = "cite:"+source;
                }
                JSONObject perspective = new JSONObject();
                JSONObject attribution = perspectiveJsonObject.getJSONObject();
                perspective.put("attribution", attribution);
                perspective.put("source", source);
                //System.out.println("source = " + source);
                mentionObject.append("perspective", perspective);
            }
            else {
                source = perspectiveJsonObject.getAuthor();
                if (!source.isEmpty()) {
                    ArrayList<String> authorAnd = Util.splitSubstring(source, "+and+");
                    for (int l = 0; l < authorAnd.size(); l++) {
                        String subauthor = authorAnd.get(l);
                        ArrayList<String> subauthorfields = Util.splitSubstring(subauthor, "%2C+");
                        for (int x = 0; x < subauthorfields.size(); x++) {
                            String subfield = subauthorfields.get(x);
                            // System.out.println("subfield = " + subfield);
                            //// Financial Times hack
/*                            if (!subfield.toLowerCase().endsWith("correspondent")
                                    && !subfield.toLowerCase().endsWith("reporter")
                                    && !subfield.toLowerCase().endsWith("editor")
                                    && !subfield.toLowerCase().startsWith("in+")
                                    ) {
                                String author = JsonStoryUtil.normalizeSourceValue(subfield);
                                author = "author:" + JsonStoryUtil.cleanAuthor(author);
                                JSONObject perspective = new JSONObject();
                                JSONObject attribution = perspectiveJsonObject.getJSONObject();
                                perspective.put("attribution", attribution);
                                perspective.put("source", author);
                                //  System.out.println("source = " + source);
                                mentionObject.append("perspective", perspective);
                            }*/
                            String author = JsonStoryUtil.normalizeSourceValue(subfield);
                            author = "author:" + JsonStoryUtil.cleanAuthor(author);
                            JSONObject perspective = new JSONObject();
                            JSONObject attribution = perspectiveJsonObject.getJSONObject();
                            perspective.put("attribution", attribution);
                            perspective.put("source", author);
                            //  System.out.println("source = " + source);
                            mentionObject.append("perspective", perspective);
                        }
                    }
                }
                else {
                    //// no cited source and no author source
                    String author =  "author:" + "unknown";
                    JSONObject perspective = new JSONObject();
                    JSONObject attribution = perspectiveJsonObject.getJSONObject();
                    perspective.put("attribution", attribution);
                    perspective.put("source", author);
                    //  System.out.println("source = " + source);
                    mentionObject.append("perspective", perspective);

                }
            }
        }
    }

    @Deprecated

    public static void integratePerspectivesInEventObjects(TrigTripleData trigTripleData, ArrayList<JSONObject> targetEvents, String meta) {
        for (int i = 0; i < targetEvents.size(); i++) {
            JSONObject mEvent = targetEvents.get(i);
            JSONArray mMentions = null;
            try {
                mMentions = (JSONArray) mEvent.get("mentions");
            } catch (JSONException e) {
            }
            if (mMentions != null) {
                for (int m = 0; m < mMentions.length(); m++) {
                    try {
                        JSONObject mentionObject = (JSONObject) mMentions.get(m);
                        String uriString = mentionObject.getString("uri");
                        JSONArray offsetArray = mentionObject.getJSONArray("char");
                        String mention = JsonStoryUtil.getStringValueforMention(uriString, offsetArray);
                        PerspectiveJsonObject perspectiveJsonObject = getPerspectiveObjectForEvent(trigTripleData, mention, meta);

                        if (perspectiveJsonObject != null) {
                            addPerspectiveToMention(mentionObject, perspectiveJsonObject);
                        } else {

                        }


/*                        if (perspectiveJsonObject.getAttribution().size()>0) {
                            //System.out.println("mention event = " + mention);
                            if (perspectiveJsonObject != null) {
                                addPerspectiveToMention(mentionObject, perspectiveJsonObject);
                            } else {

                            }
                        }*/

                    } catch (JSONException e) {
                        // e.printStackTrace();
                    }
                }
            }
        }
    }



/*
    static ArrayList<JSONObject> getPerspectiveEvents (TrigTripleData trigTripleData, ArrayList<JSONObject> jsonObjects) throws JSONException {
        ArrayList<JSONObject> pEvents = new ArrayList<JSONObject>();
        Set keySet = trigTripleData.tripleMapGrasp.keySet();
        Iterator<String> keys = keySet.iterator();
        while (keys.hasNext()) {
            String key = keys.next(); //// this is the subject of the triple which should point to an event mention
           // System.out.println("key = " + key);
            JSONObject mObject = JsonStoryFromRdf.getMentionObjectFromMentionURI(key);
            String source = "";
            String speechactLabel = "";
            String targetLabel = "";
            JSONObject speechActMention = null;
            ArrayList<String> perspectives = new ArrayList<String>();
            ArrayList<Statement> perspectiveTriples = trigTripleData.tripleMapGrasp.get(key);
            for (int i = 0; i < perspectiveTriples.size(); i++) {
                Statement statement = perspectiveTriples.get(i);
                String subject = statement.getSubject().getURI();
                String predicate = statement.getPredicate().getURI();
                String object = statement.getObject().toString();
                if (predicate.endsWith("#hasAttribution")) {
                    if (trigTripleData.tripleMapGrasp.containsKey(object)) {
                        ArrayList<Statement> perspectiveValues = trigTripleData.tripleMapGrasp.get(object);
                        for (int j = 0; j < perspectiveValues.size(); j++) {
                            Statement statement1 = perspectiveValues.get(j);
                            //ttp://www.w3.org/ns/prov#wasAttributedTo,
                            if (statement1.getPredicate().getURI().endsWith("#wasAttributedTo")) {
                               //  System.out.println("statement1.getObject().toString() = " + statement1.getObject().toString());
                                if (trigTripleData.tripleMapGrasp.containsKey(statement1.getObject().toString())) {
                                    ArrayList<Statement> provStatements = trigTripleData.tripleMapGrasp.get(statement1.getObject().toString());
                                    for (int k = 0; k < provStatements.size(); k++) {
                                        Statement statement2 = provStatements.get(k);
                                        source = statement2.getObject().toString();
                                        int idx = source.lastIndexOf("/");
                                        if (idx>-1) {
                                            source = "auth:"+source.substring(idx+1);
                                        }
                                       //  System.out.println("author source = " + source);
                                    }
                                }
                                else {
                                    source = statement1.getObject().toString();
                                    int idx = source.lastIndexOf("/");
                                    if (idx>-1) {
                                        source = "cite:"+source.substring(idx+1);
                                    }
                                   //  System.out.println("quote source = " + source);

                                }
                            }
                            else if (statement1.getPredicate().getURI().endsWith("#value")) {
                                String perspective = "";
                                String str = statement1.getObject().toString();
                             //   System.out.println("str = " + str);
                                int idx = str.lastIndexOf("#");
                                if (idx>-1) {
                                    perspective = str.substring(idx+1);
                                }
                                else {
                                    idx = str.lastIndexOf("/");
                                    if (idx>-1) {
                                        perspective = str.substring(idx+1);
                                    }
                                    else {
                                        perspective = str;
                                    }
                                }
                                if (!perspective.isEmpty() && !perspectives.contains(perspective)) {
                                   // System.out.println("perspective = " + perspective);
                                    perspectives.add(perspective);
                                }
                            }
                            else {
                             //    System.out.println("statement1.getPredicate().getURI() = " + statement1.getPredicate().getURI());
                            }
                        }
                    }
                }
                else if (predicate.endsWith("#comment"))  {
                    //rdfs:comment
                    speechactLabel = object;
                    int idx = speechactLabel.lastIndexOf("/");
                    if (idx>-1) {
                        speechactLabel = speechactLabel.substring(idx+1);
                    }
                }
                else if (predicate.endsWith("#label"))  {
                    //rdfs:label
                    targetLabel = object;
                    int idx = targetLabel.lastIndexOf("/");
                    if (idx>-1) {
                        targetLabel = targetLabel.substring(idx+1);
                    }
                }
                else if (predicate.endsWith("generatedBy"))  {
                    speechActMention = JsonStoryFromRdf.getMentionObjectFromMentionURI(object);
                }
            }
            ArrayList<String> newPerspectives = new ArrayList<String>();
            for (int j = 0; j < perspectives.size(); j++) {
                String perspective =  perspectives.get(j);
                ArrayList<String> normValues = PerspectiveJsonObject.normalizePerspectiveValue(perspective);
                if (!normValues.isEmpty()) {
                    for (int i = 0; i < normValues.size(); i++) {
                        String nv =  normValues.get(i);
                        if (!newPerspectives.contains(nv)) {
                            newPerspectives.add(nv);
                        }
                    }
                }
            }
            if (newPerspectives.size()>0) {
                Collections.sort(newPerspectives);
               // System.out.println("newPerspectives.toString() = " + newPerspectives.toString());
                boolean MATCH = false;
                JSONObject targetObject = null;
                for (int j = 0; j < jsonObjects.size(); j++) {
                    JSONObject jsonObject = jsonObjects.get(j);
                  //  System.out.println("jsonObject.toString() = " + jsonObject.toString());
                    try {
                        JSONArray mentions = (JSONArray) jsonObject.get("mentions");
                        for (int m = 0; m < mentions.length(); m++) {
                            JSONObject mentionObject = (JSONObject) mentions.get(m);
                            //System.out.println("mentionObject.toString() = " + mentionObject.toString());
                            //System.out.println("mObject.toString() = " + mObject.toString());
                            if (mentionObject.toString().equals(mObject.toString())) {
                                MATCH = true;
                                //System.out.println("mentionObject.toString() = " + mentionObject.toString());
                                targetObject = jsonObject;
                                break;
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                if (MATCH && targetObject!=null) {
                    JSONObject perspectiveEvent = null;
                    try {
                        perspectiveEvent = createSourcePerspectiveEvent(key,speechactLabel, targetLabel, source,
                                newPerspectives, mObject, targetObject, speechActMention);
                    } catch (JSONException e) {
                          e.printStackTrace();
                    }
                    pEvents.add(perspectiveEvent);
                }
            }
        }
        System.out.println("pEvents = " + pEvents.size());
        return pEvents;
    }
*/

    static public String getURIforMention (String uriValue, JSONArray charOffset) throws JSONException {
        String uri = "<"+uriValue+"#char="+charOffset.getString(0)+","+charOffset.getString(1)+">";
        //<http://www.ft.com/thing/05fc83c6-1b5c-11e5-8201-cbdb03d71480#char=19,28>
        //{"char":["1699","1706"],"uri":["http://www.ft.com/thing/03de44c8-2f96-11e5-8873-775ba7c2ea3d"]}
        return uri;
    }
    
    static public String getStringValueforMention (String uriValue, JSONArray charOffset) throws JSONException {
        String uri = uriValue+"#char="+charOffset.getString(0)+","+charOffset.getString(1);
        //<http://www.ft.com/thing/05fc83c6-1b5c-11e5-8201-cbdb03d71480#char=19,28>
        //{"char":["1699","1706"],"uri":["http://www.ft.com/thing/03de44c8-2f96-11e5-8873-775ba7c2ea3d"]}
        return uri;
    }

/*    static public String getURIforMention (JSONArray uriValue, JSONArray charOffset) throws JSONException {
        String uri = "<"+uriValue.getString(0)+"#char="+charOffset.getString(0)+","+charOffset.getString(1)+">";
        //<http://www.ft.com/thing/05fc83c6-1b5c-11e5-8201-cbdb03d71480#char=19,28>
        //{"char":["1699","1706"],"uri":["http://www.ft.com/thing/03de44c8-2f96-11e5-8873-775ba7c2ea3d"]}
        return uri;
    }

    static public String getStringValueforMention (JSONArray uriValue, JSONArray charOffset) throws JSONException {
        String uri = uriValue.getString(0)+"#char="+charOffset.getString(0)+","+charOffset.getString(1);
        //<http://www.ft.com/thing/05fc83c6-1b5c-11e5-8201-cbdb03d71480#char=19,28>
        //{"char":["1699","1706"],"uri":["http://www.ft.com/thing/03de44c8-2f96-11e5-8873-775ba7c2ea3d"]}
        return uri;
    }*/

    @Deprecated
    /*static ArrayList<JSONObject> getPerspectiveEventsFromKS (ArrayList<JSONObject> jsonObjects) throws JSONException {
        ArrayList<JSONObject> pEvents = new ArrayList<JSONObject>();
        int totalMentions = 0;
        for (int i = 0; i < jsonObjects.size(); i++) {
            JSONObject jsonObject = jsonObjects.get(i);
            try {
                JSONArray mentions = (JSONArray) jsonObject.get("mentions");
                totalMentions += mentions.length();
                System.out.println("mentions.length() = " + mentions.length());
                /// for each metion of an event, we check if there is a perspective on it.
                for (int m = 0; m < mentions.length(); m++) {
                    JSONObject mentionObject = (JSONObject) mentions.get(m);
                    JSONObject speechActMention = null;
                    ArrayList<String> perspectives = new ArrayList<String>();
                    String source = "";
                    String speechactLabel = "";
                    String targetLabel = "";
                    String uriString = mentionObject.getString("uri");
                    JSONArray offsetArray = mentionObject.getJSONArray("char");
                    String mention = getURIforMention(uriString, offsetArray);
                    System.out.println("event mention = " + mention);
                    String sparqlQuery = makeTripleQuery(mention);
                    ArrayList<Statement> perspectiveTriples = GetTriplesFromKnowledgeStore.readTriplesFromKs(mention, sparqlQuery);
                    System.out.println("perspectiveTriples.size() = " + perspectiveTriples.size());
                    for (int p = 0; p < perspectiveTriples.size(); p++) {
                        Statement statement = perspectiveTriples.get(p);
                        String relString = statement.getPredicate().toString();
                        String objUri = statement.getObject().toString();
                        if (GetTriplesFromKnowledgeStore.isAttributionRelation(relString)) {
                            sparqlQuery = makeTripleQuery(objUri);
                            ArrayList<Statement> attrTriples = GetTriplesFromKnowledgeStore.readTriplesFromKs(objUri, sparqlQuery);
                            System.out.println("attrTriples.size() = " + attrTriples.size());
                            boolean hasPerspective = false;
                            for (int j = 0; j < attrTriples.size(); j++) {
                                Statement attrStatement = attrTriples.get(j);
                                if (attrStatement.getPredicate().getURI().endsWith("#value")) {
                                    String perspective = "";
                                    String str = attrStatement.getObject().toString();
                                    //   System.out.println("str = " + str);
                                    int idx = str.lastIndexOf("#");
                                    if (idx>-1) {  perspective = str.substring(idx+1); }
                                    else {
                                        idx = str.lastIndexOf("/");
                                        if (idx>-1) { perspective = str.substring(idx+1);  }
                                        else {  perspective = str;  }
                                    }
                                    ArrayList<String> normValues = PerspectiveJsonObject.normalizePerspectiveValue(perspective);
                                    if (!normValues.isEmpty()) {



                                        for (int k = 0; k < normValues.size(); k++) {
                                            String nv = normValues.get(k);
                                            hasPerspective = true;
                                            perspectives.add(nv);
                                        }
                                    }
                                }
                            }
                            if (hasPerspective) {
                                for (int j = 0; j < attrTriples.size(); j++) {
                                    Statement attrStatement = attrTriples.get(j);
                                    if (attrStatement.getPredicate().getURI().endsWith("#wasAttributedTo")) {
                                        //  System.out.println("statement1.getObject().toString() = " + statement1.getObject().toString());
                                        String attrObj = attrStatement.getObject().toString();
                                        sparqlQuery = makeTripleQuery(attrObj);
                                        ArrayList<Statement> docTriples = GetTriplesFromKnowledgeStore.readTriplesFromKs(objUri, sparqlQuery);
                                        if (docTriples.size() > 0) {
                                            for (int d = 0; d < docTriples.size(); d++) {
                                                Statement docStatement = docTriples.get(d);
                                                source = docStatement.getObject().toString();
                                                int idx = source.lastIndexOf("/");
                                                if (idx > -1) {
                                                    source = "auth:" + source.substring(idx + 1);
                                                }
                                            }
                                        } else {
                                            source = attrStatement.getObject().toString();
                                            int idx = source.lastIndexOf("/");
                                            if (idx > -1) {
                                                source = "cite:" + source.substring(idx + 1);
                                            }
                                            //  System.out.println("quote source = " + source);

                                        }
                                    }
                                }
                            }
                            else {
                                break;
                            }
                        } else if (relString.endsWith("#comment")) {
                            //rdfs:comment
                            speechactLabel = objUri;
                            int idx = speechactLabel.lastIndexOf("/");
                            if (idx > -1) {
                                speechactLabel = speechactLabel.substring(idx + 1);
                            }
                        } else if (relString.endsWith("#label")) {
                            //rdfs:label
                            targetLabel = objUri;
                            int idx = targetLabel.lastIndexOf("/");
                            if (idx > -1) {
                                targetLabel = targetLabel.substring(idx + 1);
                            }
                        } else if (relString.endsWith("generatedBy")) {
                            speechActMention = JsonStoryFromRdf.getMentionObjectFromMentionURI(objUri);
                        }
                        else {
                            /// WE IGNORE THIS TRIPLE
                        }
                    }
                    if (perspectives.size() > 0) {
                        Collections.sort(perspectives);
                        System.out.println("perspectives.toString() = " + perspectives.toString());
                        JSONObject perspectiveEvent = createSourcePerspectiveEvent(mention, speechactLabel, targetLabel, source,
                                perspectives, mentionObject, jsonObject, speechActMention);
                        pEvents.add(perspectiveEvent);

                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        System.out.println("totalMentions = " + totalMentions);
        System.out.println("pEvents = " + pEvents.size());
        return pEvents;
    }
*/

    ///"source": "author:Matt_Steinglass_in_Amsterdam"
    ///"source": "author:George_Parker?_Political_Editor"
    //"source": "author:Elizabeth_Rigby_and_George_Parker_in_London_and_Quentin_Peel_in_Berlin"
    //"source": "author:Kate_Allen_and_George_Parker"
    //"source": "author:Alex_Barker?_European_diplomatic_editor"
    //"source": "author:Alex_Barker_and_Stefan_Wagstyl_in_Brussels_and_Henry_Foy_in_Warsaw"

    static public ArrayList<String> splitAuthors (String source) {
        ArrayList<String> authors = new ArrayList<String>();
        int idx = source.indexOf("_and_");
        if (idx>-1) {
            String[] fields = source.split("_and_");
            for (int i = 0; i < fields.length; i++) {
                String field = cleanAuthor(fields[i]);
                if (i>0) {
                    field = "author:"+field;
                }
                authors.add(field);
            }
        }
       // System.out.println(authors.toString());
        return authors;
    }


    public static String cleanAuthor(String author) {
        String cleanAuthor = author;
        int idx = author.indexOf("_in_");
        if (idx>-1) {
            cleanAuthor = author.substring(0, idx);
        }
        idx = cleanAuthor.indexOf("_at_");
        if (idx>-1) {
            cleanAuthor = cleanAuthor.substring(0, idx);
        }
        idx = cleanAuthor.indexOf("?_");
        if (idx>-1) {
            cleanAuthor = cleanAuthor.substring(0, idx);
        }
        return cleanAuthor;
    }

    public static String cleanCite(String cite) {
        String cleanCite = cite;
        if (cite.startsWith("Mrs")) {
            cleanCite = cite.substring(3);
        }
        else if (cite.startsWith("Mr")) {
            cleanCite = cite.substring(2);
        }
        else if (cite.startsWith("Ms")) {
            cleanCite = cite.substring(2);
        }

        return cleanCite;
    }

    static public String normalizeSourceValue (String value) {
        String normValue = "";
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c=='+') {
                normValue+="_";
            }
            else {
                normValue+=c;
            }
        }
        int idx = normValue.toLowerCase().indexOf("_(disambiguation)");
        if (idx>-1) {
            normValue = normValue.substring(0, idx).trim();
        }
        if (normValue.toLowerCase().startsWith("review_by_")) {
            normValue = normValue.substring(10);
        }
        if (normValue.toLowerCase().startsWith("by_")) {
            normValue = normValue.substring(3);
        }
        idx = normValue.indexOf(":By_");
        if (idx>-1) {
            normValue = normValue.substring(0, idx+1).trim()+normValue.substring(idx+4);
        }
        idx = normValue.indexOf("%");
        while (idx>-1) {
            String rest ="";
            if (normValue.length()>idx+3); {
                rest = normValue.substring(idx+3);
            }
            normValue = normValue.substring(0, idx);
            if (rest.indexOf("%")>-1) {
                normValue+=rest;
                idx = normValue.indexOf("%");
            }
            else {
                normValue +="?"+rest;
                idx=-1;
            }
        }
        // System.out.println("value = " + value);
        // System.out.println("normValue = " + normValue);
        return normValue;
    }



    static public JSONObject createSourcePerspectiveEvent (String key,
                                                    String speechActLabel,
                                                    String targetLabel,
                                                    String source,
                                                    ArrayList<String> perspectives,
                                                    JSONObject mention,
                                                    JSONObject targetEvent,
                                                    JSONObject speechActMention) throws JSONException {
        JSONObject sourcePerspectiveEvent = new JSONObject();
        String prefLabel = "";
        String factualityLabel = "";
        String time = "";
        String climax = "";
        String group = "";
        String groupName="";
        String groupScore="";
        String targetEventId = "";

        if (speechActMention!=null) {
            sourcePerspectiveEvent.append("mentions", speechActMention);
        }
        else {
            sourcePerspectiveEvent.append("mentions", mention);
        }
        sourcePerspectiveEvent.put("instance", key);
        //sourcePerspectiveEvent.put("event", ""));


        /// labels
        for (int i = 0; i < perspectives.size(); i++) {
            String s =  perspectives.get(i);
            if (s.equals(":(")) {
                prefLabel += s;
            }
            else if (s.equals(":)")) {
                prefLabel += s;
            }
            else {
                factualityLabel+=s;
            }
        }
        if (prefLabel.isEmpty()) {
            prefLabel = ":|";
        }
        prefLabel += "-"+speechActLabel+"-"+targetLabel+"-"+factualityLabel;
        sourcePerspectiveEvent.append("prefLabel", prefLabel);
        sourcePerspectiveEvent.append("labels", prefLabel);

        time = targetEvent.getString("time");

        source = normalizeSourceValue(source);
        JSONObject jsonActorsObject = new JSONObject();
        jsonActorsObject.append("", source);
        sourcePerspectiveEvent.put("actors", jsonActorsObject);

        climax = targetEvent.getString("climax");
        group = targetEvent.getString("group");
        groupName = targetEvent.getString("groupName");
        groupScore=targetEvent.getString("groupScore");

        climax = "1";

        targetEventId = targetEvent.getString("instance");

        sourcePerspectiveEvent.put("instance", key);
        sourcePerspectiveEvent.put("climax", climax);
        sourcePerspectiveEvent.put("time", time);
        sourcePerspectiveEvent.put("group", group);
        sourcePerspectiveEvent.put("groupName", groupName);
        sourcePerspectiveEvent.put("groupScore", groupScore);
        sourcePerspectiveEvent.put("target", targetEventId);
        return sourcePerspectiveEvent;
    }


    static public JSONObject createSourcePerspectiveEvent (PerspectiveJsonObject perspectiveJsonObject) throws JSONException {
        JSONObject sourcePerspectiveEvent = new JSONObject();
        String prefLabel = "";
        String factualityLabel = "";
        String time = "";
        String climax = "";
        String group = "";
        String groupName="";
        String groupScore="";


        sourcePerspectiveEvent.append("mentions", perspectiveJsonObject.getMention());

        sourcePerspectiveEvent.put("instance", perspectiveJsonObject.getMention());

        /// labels
        for (int i = 0; i < perspectiveJsonObject.getAttribution().size(); i++) {
            String s =  perspectiveJsonObject.getAttribution().get(i);
            if (s.equals(":(")) {
                prefLabel += s;
            }
            else if (s.equals(":)")) {
                prefLabel += s;
            }
            else {
                factualityLabel+=s;
            }
        }
        if (prefLabel.isEmpty()) {
            prefLabel = ":|";
        }
        prefLabel += "-"+perspectiveJsonObject.getComment()+"-"+perspectiveJsonObject.getLabel()+"-"+factualityLabel;
        sourcePerspectiveEvent.append("prefLabel", prefLabel);
        sourcePerspectiveEvent.append("labels", prefLabel);
        JSONObject targetEvent = perspectiveJsonObject.getTargeEvent();
        time = targetEvent.getString("time");

        String source = normalizeSourceValue(perspectiveJsonObject.getSource());
        JSONObject jsonActorsObject = new JSONObject();
        jsonActorsObject.append("", source);
        sourcePerspectiveEvent.put("actors", jsonActorsObject);

        climax = targetEvent.getString("climax");
        group = targetEvent.getString("group");
        groupName = targetEvent.getString("groupName");
        groupScore=targetEvent.getString("groupScore");

        climax = "1";

        sourcePerspectiveEvent.put("climax", climax);
        sourcePerspectiveEvent.put("time", time);
        sourcePerspectiveEvent.put("group", group);
        sourcePerspectiveEvent.put("groupName", groupName);
        sourcePerspectiveEvent.put("groupScore", groupScore);
        return sourcePerspectiveEvent;
    }

    static public void augmentEventLabelsWithArguments (ArrayList<JSONObject> events) throws JSONException {
        for (int i = 0; i < events.size(); i++) {
            JSONObject jsonObject = events.get(i);
            String mainActor = getfirstActorByRoleFromEvent(jsonObject, "pb/A1"); /// for representation purposes
            if (mainActor.isEmpty()) {
                mainActor = getfirstActorByRoleFromEvent(jsonObject, "pb/A0");
            }
            if (mainActor.isEmpty()) {
                mainActor = getfirstActorByRoleFromEvent(jsonObject, "pb/A2");
            }
            System.out.println("mainActor = " + mainActor);
            if (!mainActor.isEmpty()) {
                JSONObject jsonLabelObject = new JSONObject();

                JSONArray labelArray = jsonObject.getJSONArray("labels");
                for (int j = 0; j < labelArray.length(); j++) {
                    String label = labelArray.getString(j);
                    if (i==0) {
                        label+=mainActor;
                    }
                    jsonLabelObject.append("labels",label);
                }
                jsonObject.put("labels", jsonLabelObject.get("labels"));
            }
        }
    }

    public static ArrayList<JSONObject> getEventsThroughTopicBridging(ArrayList<JSONObject> events,
                                                                      JSONObject event,
                                                                      int topicThreshold) {
        ArrayList<JSONObject> topicEvents = new ArrayList<JSONObject>();
        boolean DEBUG = false;
        if (DEBUG) System.out.println("bridging");
        try {
            JSONArray topics = null;
            try {
                topics = (JSONArray) event.get("topics");
            } catch (JSONException e) {
                if (DEBUG)  e.printStackTrace();
            }
            if (topics!=null) {
                if (DEBUG) {
                    System.out.println("topics = " + topics.length());
                    System.out.println("topics.toString() = " + topics.toString());
                }
                for (int i = 0; i < events.size(); i++) {
                    JSONObject oEvent = events.get(i);
                    if (!oEvent.equals(event)) {
                        JSONArray oTopics = null;
                        try {
                            oTopics = (JSONArray) oEvent.get("topics");
                        } catch (JSONException e) {
                            if (DEBUG)   e.printStackTrace();
                        }
                        if (oTopics!=null) {
                            if (DEBUG) {
                                System.out.println("otopics = " + oTopics.length());
                                System.out.println("oTopics.toString() = " + oTopics.toString());
                            }
                            int sharedTopics = 0;
                            for (int j = 0; j < topics.length(); j++) {
                                String topic = (String) topics.get(j);
                                for (int k = 0; k < oTopics.length(); k++) {
                                    String oTopic = (String) oTopics.get(k);
                                    if (topic.equals(oTopic)) {
                                        sharedTopics++;
                                    }
                                }
                            }
                            int prop1 = sharedTopics*100/topics.length();
                            int prop2 = sharedTopics*100/oTopics.length();
                            if (DEBUG) {
                                if (sharedTopics > 0) {
                                    System.out.println("sharedTopics = " + sharedTopics);
                                    System.out.println("prop1 = " + prop1);
                                    System.out.println("prop2 = " + prop2);
                                }
                            }

                            if (prop1>=topicThreshold && prop2>= topicThreshold) {
                                // System.out.println("topicThreshold = " + topicThreshold);
                                if (!topicEvents.contains(oEvent)) {
/*
                                    System.out.println("prop1 = " + prop1);
                                    System.out.println("prop2 = " + prop2);
                                    System.out.println("oEvent = " + oEvent);
*/
                                    topicEvents.add(oEvent);
                                    //System.out.println("topicEvents = " + topicEvents.size());
                                }
                            }
                        }
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return topicEvents;
    }

    public static ArrayList<JSONObject> getEventsThroughFrameNetBridging(ArrayList<JSONObject> events,
                                                                         JSONObject event,
                                                                         FrameNetReader frameNetReader)
            throws JSONException {
        ArrayList<JSONObject> fnRelatedEvents = new ArrayList<JSONObject>();
        JSONArray superFrames = (JSONArray) event.get("fnsuperframes");
        if (superFrames!=null) {
            for (int j = 0; j < superFrames.length(); j++) {
                String frame = (String) superFrames.get(j);
                for (int i = 0; i < events.size(); i++) {
                    JSONObject oEvent = events.get(i);
                    if (!oEvent.equals(event)) {
                        JSONArray oSuperFrames = (JSONArray) oEvent.get("fnsuperframes");
                        for (int k = 0; k < oSuperFrames.length(); k++) {
                            String oFrame = (String) oSuperFrames.get(k);
                            if (frame.equals(oFrame)) {
                                if (!fnRelatedEvents.contains(oEvent)) {
                                    fnRelatedEvents.add(oEvent);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
        return fnRelatedEvents;
    }

    public static ArrayList<JSONObject> getEventsThroughEsoBridging(ArrayList<JSONObject> events,
                                                                    JSONObject event,
                                                                    FrameNetReader frameNetReader)
            throws JSONException {
        ArrayList<JSONObject> esoRelatedEvents = new ArrayList<JSONObject>();
        JSONArray superFrames = (JSONArray) event.get("esosuperclasses");
        if (superFrames!=null) {
            for (int j = 0; j < superFrames.length(); j++) {
                String frame = (String) superFrames.get(j);
                for (int i = 0; i < events.size(); i++) {
                    JSONObject oEvent = events.get(i);
                    if (!oEvent.equals(event)) {
                        JSONArray oSuperFrames = (JSONArray) oEvent.get("esosuperclasses");
                        for (int k = 0; k < oSuperFrames.length(); k++) {
                            String oFrame = (String) oSuperFrames.get(k);
                            if (frame.equals(oFrame)) {
                                if (!esoRelatedEvents.contains(oEvent)) {
                                    esoRelatedEvents.add(oEvent);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
        return esoRelatedEvents;
    }

    public static ArrayList<JSONObject> getEventsThroughCoparticipation(ArrayList<JSONObject> events,
                                                                        JSONObject event, int intersection)
            throws JSONException {
        ArrayList<JSONObject> coPartipantEvents = new ArrayList<JSONObject>();
        JSONObject actorObject = null;
        try {
            actorObject = event.getJSONObject("actors");
            Iterator keys = actorObject.sortedKeys();
            while (keys.hasNext()) {
                String key = keys.next().toString(); //role
                if (key.toLowerCase().startsWith("pb/")
                        || key.toLowerCase().startsWith("fn/")
                        || key.toLowerCase().startsWith("eso/")
                        ) {
                    JSONArray actors = actorObject.getJSONArray(key);
                    for (int i = 0; i < events.size(); i++) {
                        JSONObject oEvent = events.get(i);
                        if (!oEvent.get("instance").toString().equals(event.get("instance").toString())) {
                            ///skip event itself
                            JSONObject oActorObject = null;
                            try {
                                oActorObject = oEvent.getJSONObject("actors");
                                Iterator oKeys = oActorObject.sortedKeys();
                                int cnt = 0;
                                while (oKeys.hasNext()) {
                                    String oKey = oKeys.next().toString();
                                    if (oKey.toLowerCase().startsWith("pb/")
                                            || oKey.toLowerCase().startsWith("fn/")
                                            || oKey.toLowerCase().startsWith("eso/")
                                            ) {
                                        JSONArray oActors = oActorObject.getJSONArray(oKey);
                                        //  System.out.println("oActors.length() = " + oActors.length());
                                        // System.out.println("oActors.toString() = " + oActors.toString());
                                        if (countIntersectingDBpActor(actors,oActors)>=intersection) {
                                            cnt++;
                                            if (!coPartipantEvents.contains(oEvent)) {
                                                coPartipantEvents.add(oEvent);
                                            }
                                            break;
                                        }
                                    }
                                }
                            } catch (JSONException e) {
                               // e.printStackTrace();
                            }
                        }
                    }
                }
            }
        } catch (JSONException e) {
           // e.printStackTrace();
        }
        return coPartipantEvents;
    }

    public static ArrayList<JSONObject> getEventsThroughCoparticipation(String entityFilter, ArrayList<JSONObject> events,
                                                                        JSONObject event)
            throws JSONException {
        ArrayList<JSONObject> coPartipantEvents = new ArrayList<JSONObject>();
        JSONObject actorObject = event.getJSONObject("actors");
        Iterator keys = actorObject.sortedKeys();
        while (keys.hasNext()) {
            String key = keys.next().toString(); //role
            //  System.out.println("key = " + key);
            if (key.toLowerCase().startsWith("pb/")
                    || key.toLowerCase().startsWith("fn/")
                    || key.toLowerCase().startsWith("eso/")) {
                JSONArray actors = actorObject.getJSONArray(key);
                // System.out.println("actors.toString() = " + actors.toString());
                for (int i = 0; i < events.size(); i++) {
                    JSONObject oEvent = events.get(i);
                    if (!oEvent.get("instance").toString().equals(event.get("instance").toString())) {
                        ///skip event itself
                        JSONObject oActorObject = oEvent.getJSONObject("actors");
                        Iterator oKeys = oActorObject.sortedKeys();
                        while (oKeys.hasNext()) {
                            String oKey = oKeys.next().toString();
                            if (oKey.toLowerCase().startsWith("pb/")
                                    || oKey.toLowerCase().startsWith("fn/")
                                    || oKey.toLowerCase().startsWith("eso/")) {
                                JSONArray oActors = oActorObject.getJSONArray(oKey);
                                // System.out.println("oActors.toString() = " + oActors.toString());
                                if (intersectingActor(entityFilter, actors, oActors)) {
                                    if (!coPartipantEvents.contains(oEvent)) {
                                        coPartipantEvents.add(oEvent);
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        return coPartipantEvents;
    }

    public static ArrayList<JSONObject> getEventsThroughCoparticipation(ArrayList<JSONObject> events,
                                                                        ArrayList<JSONObject> storyEvents)
            throws JSONException {
        ArrayList<JSONObject> coPartipantEvents = new ArrayList<JSONObject>();
        for (int j = 0; j < events.size(); j++) {
            JSONObject jsonObject = events.get(j);
            JSONObject actorObject = jsonObject.getJSONObject("actors");
            Iterator keys = actorObject.sortedKeys();
            while (keys.hasNext()) {
                String key = keys.next().toString(); //role
                //  System.out.println("key = " + key);
                if (key.equalsIgnoreCase("pb/A0")
                        || key.equalsIgnoreCase("pb/A1")
                        || key.equalsIgnoreCase("pb/A2")
                        || key.equalsIgnoreCase("pb/A3")
                        || key.equalsIgnoreCase("pb/A4")
                        || key.toLowerCase().startsWith("fn/")
                        || key.toLowerCase().startsWith("eso/")) {
                    JSONArray actors = actorObject.getJSONArray(key);
                    // System.out.println("actors.toString() = " + actors.toString());
                    for (int i = 0; i < storyEvents.size(); i++) {
                        JSONObject oEvent = storyEvents.get(i);
                        if (!oEvent.get("instance").toString().equals(jsonObject.get("instance").toString())) {
                            ///skip event itself
                            JSONObject oActorObject = oEvent.getJSONObject("actors");
                            Iterator oKeys = oActorObject.sortedKeys();
                            while (oKeys.hasNext()) {
                                String oKey = oKeys.next().toString();
                                if (oKey.equalsIgnoreCase("pb/A0")
                                        || oKey.equalsIgnoreCase("pb/A1")
                                        || oKey.equalsIgnoreCase("pb/A2")
                                        || oKey.equalsIgnoreCase("pb/A3")
                                        || oKey.equalsIgnoreCase("pb/A4")
                                        || oKey.toLowerCase().startsWith("fn/")
                                        || oKey.toLowerCase().startsWith("eso/")) {
                                    JSONArray oActors = oActorObject.getJSONArray(oKey);
                                    // System.out.println("oActors.toString() = " + oActors.toString());
                                    if (intersectingDBpActor(actors, oActors)) {
                                        if (!coPartipantEvents.contains(oEvent)) {
                                            coPartipantEvents.add(oEvent);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return coPartipantEvents;
    }

    static boolean intersectingActor (JSONArray actors, JSONArray oActors) throws JSONException {
        for (int j = 0; j < actors.length(); j++) {
            String actor = actors.getString(j);
            for (int k = 0; k < oActors.length(); k++) {
                String oActor = oActors.getString(k);
                if (actor.equals(oActor)) {
                    return true;
                }
            }
        }
        return false;
    }

    static int countIntersectingDBpActor (JSONArray actors, JSONArray oActors) throws JSONException {
        int cnt = 0;
        for (int j = 0; j < actors.length(); j++) {
            String actor = actors.getString(j);
            //System.out.println("actor = " + actor);
            for (int k = 0; k < oActors.length(); k++) {
                String oActor = oActors.getString(k);
                if (actor.equals(oActor)) {
                    cnt++;
                }
            }
/*
            if (actor.indexOf("dbpedia")>-1 || actor.indexOf("dbp:")>-1 || actor.indexOf("en:")>-1) {
                for (int k = 0; k < oActors.length(); k++) {
                    String oActor = oActors.getString(k);
                    if (oActor.indexOf("dbpedia")>-1 || actor.indexOf("dbp:")>-1 || actor.indexOf("en:")>-1) {
                        if (actor.equals(oActor)) {
                            cnt++;
                        }
                    }
                }
            }
*/
        }
        return cnt;
    }

    static boolean intersectingDBpActor (JSONArray actors, JSONArray oActors) throws JSONException {
        for (int j = 0; j < actors.length(); j++) {
            String actor = actors.getString(j);
            //System.out.println("actor = " + actor);
            if (actor.indexOf("dbpedia")>-1 || actor.indexOf("dbp:")>-1 || actor.indexOf("en:")>-1) {
                for (int k = 0; k < oActors.length(); k++) {
                    String oActor = oActors.getString(k);
                    if (oActor.indexOf("dbpedia")>-1 || actor.indexOf("dbp:")>-1 || actor.indexOf("en:")>-1) {
                        if (actor.equals(oActor)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    static boolean intersectingActor (String entityFilter, JSONArray actors, JSONArray oActors) throws JSONException {
        for (int j = 0; j < actors.length(); j++) {
            String actor = actors.getString(j);
            for (int k = 0; k < oActors.length(); k++) {
                String oActor = oActors.getString(k);
                if (actor.equals(oActor)) {
                    if (actor.indexOf(entityFilter)==-1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
