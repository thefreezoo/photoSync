package com.example.pho;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

public class SyncFragment extends Fragment {

    private ListView syncListView;
    private TextView noSyncRecordsText;
    private SyncRecordAdapter syncRecordAdapter;
    private List<SyncRecord> syncRecordList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sync, container, false);
        syncListView = view.findViewById(R.id.sync_list);
        noSyncRecordsText = view.findViewById(R.id.no_sync_records_text);
        syncRecordList = new ArrayList<>();
        syncRecordAdapter = new SyncRecordAdapter(getContext(), syncRecordList);
        syncListView.setAdapter(syncRecordAdapter);

        loadSyncRecords();

        return view;
    }

    private void loadSyncRecords() {
        // In a real app, this would load sync records from a database
        // For now, we'll just add some dummy data
        syncRecordList.clear();
        syncRecordList.add(new SyncRecord(1, "photo1.jpg", "/photos/photo1.jpg", System.currentTimeMillis(), true, null));
        syncRecordList.add(new SyncRecord(2, "photo2.jpg", "/photos/photo2.jpg", System.currentTimeMillis() - 3600000, true, null));
        syncRecordList.add(new SyncRecord(3, "photo3.jpg", "/photos/photo3.jpg", System.currentTimeMillis() - 7200000, false, "Connection error"));

        syncRecordAdapter.notifyDataSetChanged();
        updateUI();
    }

    private void updateUI() {
        if (syncRecordList.isEmpty()) {
            noSyncRecordsText.setVisibility(View.VISIBLE);
            syncListView.setVisibility(View.GONE);
        } else {
            noSyncRecordsText.setVisibility(View.GONE);
            syncListView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSyncRecords();
    }
}
