package org.ivy.settlement.core.chain.consensus.sequence.store;

import org.ivy.settlement.core.chain.consensus.sequence.model.Vote;

import java.io.*;
import java.util.HashMap;
import java.util.Optional;


/**
 * description:
 * @author carrot
 */
public class PersistentSafetyStore {

    public static final String EPOCH = "epoch";

    //highest_vote_round
    public static final String LAST_VOTED_ROUND = "last_voted_round";

    //highest_qc_round
    public static final String ONE_CHAIN_ROUND = "one_chain_round";

    //two_chain_round
    public static final String PREFERRED_ROUND = "preferred_round";


    public static abstract class Store {

        //if key create a exist key return true at once, or else do put and return false;
        public boolean create_if_not_exists(String key, String value) {
            return !create(key, value);
        }

        //if key exist and return false, or else do put and return true;
        abstract boolean create(String key, String value);

        abstract String get(String key);

        //if key not exist and return false, or else update key and return true;
        abstract boolean set(String key, String value);
    }

    public static class InMemoryStore extends Store {

        HashMap<String, String> date;

        public InMemoryStore() {
            this.date = new HashMap<>();
        }

        @Override
        public boolean create(String key, String value) {
            if (date.containsKey(key)) return false;

            date.put(key, value);
            return true;
        }

        @Override
        public String get(String key) {
            return date.get(key);
        }

        @Override
        public boolean set(String key, String value) {
            if (!date.containsKey(key)) return false;
            date.put(key, value);
            return true;
        }
    }


    private Store store;

    private Optional<Vote> lastVote;

    public PersistentSafetyStore(boolean inMemory) {
        this.store = new InMemoryStore();
        initStore();
        this.lastVote = Optional.empty();
    }

    private void initStore() {
        store.create_if_not_exists(EPOCH, String.valueOf(1));
        store.create_if_not_exists(LAST_VOTED_ROUND, String.valueOf(0));
        store.create_if_not_exists(PREFERRED_ROUND, String.valueOf(0));
        store.create_if_not_exists(ONE_CHAIN_ROUND, String.valueOf(0));
    }

    public void setEpoch(long epoch) {
        store.set(EPOCH, Long.toString(epoch));
    }

    public long getEpoch() {
        String result = this.store.get(EPOCH);
        return result != null? Long.parseLong(result) : 1;
    }

    public void setLastVotedRound(long round) {
        store.set(LAST_VOTED_ROUND, Long.toString(round));
    }

    public long getLastVotedRound() {
        String result = this.store.get(LAST_VOTED_ROUND);
        return result != null? Long.parseLong(result) : 0;
    }

    public void setPreferredRound(long round) {
        store.set(PREFERRED_ROUND, Long.toString(round));
    }

    public long getPreferredRound() {
        String result = this.store.get(PREFERRED_ROUND);
        return result != null? Long.parseLong(result) : 0;
    }

    public void setOneChainRound(long round) {
        store.set(ONE_CHAIN_ROUND, Long.toString(round));
    }

    public long getOneChainRound() {
        String result = this.store.get(ONE_CHAIN_ROUND);
        return result != null? Long.parseLong(result) : 0;
    }

    public Optional<Vote> getLastVote() {
        return lastVote;
    }

    public void setLastVote(Optional<Vote> lastVote) {
        this.lastVote = lastVote;
    }

    //    public Waypoint getWaypoint() {
//        return Waypoint.build(storage.get(WAYPOINT));
//    }


    public static void main(String[] args) throws IOException {
        PersistentSafetyStore storage = new PersistentSafetyStore(false);
        System.out.println(storage.getEpoch());
        System.out.println(storage.getLastVotedRound());
        System.out.println(storage.getPreferredRound());
        //storage.setEpoch(2);
        //storage.setLastVotedRound(3);
        //storage.setPreferredRound(2);
        System.out.println(storage.getEpoch());
        System.out.println(storage.getLastVotedRound());
        System.out.println(storage.getPreferredRound());
    }
}
