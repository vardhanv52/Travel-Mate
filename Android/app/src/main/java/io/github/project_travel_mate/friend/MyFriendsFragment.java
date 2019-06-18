package io.github.project_travel_mate.friend;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.github.project_travel_mate.R;
import objects.User;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import utils.Constants;
import utils.Utils;

import static android.app.Activity.RESULT_OK;
import static android.view.View.GONE;
import static utils.Constants.API_LINK_V2;
import static utils.Constants.USER_TOKEN;

public class MyFriendsFragment extends Fragment {
    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;
    private Dialog mDialog;
    private final List<UserRecord> mFriends = new ArrayList<>();
    private Context mContext;
    private FriendsAdapter mFriendsAdapter;
    private TextView mNoFriends;
    private JSONArray mFriendsArray;

    public MyFriendsFragment() {
        // Required empty public constructor
    }

    public static MyFriendsFragment newInstance() {
        return new MyFriendsFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_my_friends, container, false);
        ButterKnife.bind(this, view);
        view.findViewById(R.id.addFriendsFAB).setOnClickListener(onClickListener);
        mContext = getContext();
        mNoFriends = view.findViewById(R.id.noFriends);
        String friends = Utils.getUserData(mContext, Constants.FriendsList).trim();
        if (friends.length() == 0) {
            mFriendsArray = new JSONArray();
            mNoFriends.setVisibility(View.VISIBLE);
        } else {
            mNoFriends.setVisibility(GONE);
            try {
                mFriendsArray = new JSONArray(friends);
                for (int i = 0; i < mFriendsArray.length(); i++) {
                    JSONObject jsonObject = mFriendsArray.getJSONObject(i);
                    mFriends.add(new UserRecord(jsonObject.getString("name"), jsonObject.getString("mobile")));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        LinearLayoutManager manager = new LinearLayoutManager(mContext, LinearLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(manager);
        mFriendsAdapter = new FriendsAdapter();
        recyclerView.setAdapter(mFriendsAdapter);
        return view;
    }

    View.OnClickListener onClickListener = v -> {
        switch (v.getId()) {
            case R.id.addFriendsFAB:
                Intent contactPickerIntent = new Intent(Intent.ACTION_PICK,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                startActivityForResult(contactPickerIntent, 1);
                break;
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                Cursor cursor = mContext.getContentResolver().query(uri,
                        null, null, null, null);
                cursor.moveToFirst();
                String name = cursor.getString(cursor.getColumnIndex(ContactsContract.
                        Contacts.DISPLAY_NAME));
                cursor.moveToFirst();
                String number = cursor.getString(cursor.getColumnIndex(ContactsContract.
                        CommonDataKinds.Phone.NUMBER));
                Log.d("onActivityResult: ", name + " " + number);
                AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                builder.setCancelable(false);
                builder.setTitle("Confirmation");
                builder.setMessage("Are you sure you want to add " + name + " to your contacts list?");
                builder.setPositiveButton("YES", (dialog, which) -> {
                    try {
                        JSONObject object = new JSONObject();
                        object.put("name", name);
                        object.put("mobile", number);
                        mFriendsArray.put(object);
                        Utils.storeUserData(mContext, Constants.FriendsList, mFriendsArray.toString());
                        mFriends.add(new UserRecord(name, number));
                        mFriendsAdapter.notifyDataSetChanged();
                        if (mFriends.size() != 0)
                            mNoFriends.setVisibility(GONE);
                        else
                            mNoFriends.setVisibility(View.VISIBLE);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                builder.setNegativeButton("NO", (dialog, which) -> { });
                builder.show();
            }
        }
    }

    class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter
            .FriendsViewHolder> {
        @NonNull
        @Override
        public FriendsViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            return new FriendsViewHolder(LayoutInflater.from(mContext).
                    inflate(R.layout.item_friend_rv, viewGroup, false));
        }

        @Override
        public void onBindViewHolder(@NonNull FriendsViewHolder holder, int i) {
            UserRecord user = mFriends.get(holder.getAdapterPosition());
            holder.sno.setText((holder.getAdapterPosition() + 1) + ".");
            holder.name.setText(user.getmName());
            holder.mobile.setText(user.getmMobile());
        }

        @Override
        public int getItemCount() {
            return mFriends.size();
        }

        class FriendsViewHolder extends RecyclerView.ViewHolder {
            TextView name, mobile, sno;
            public FriendsViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.contact_name);
                mobile = itemView.findViewById(R.id.mobile);
                sno = itemView.findViewById(R.id.sno);
                itemView.findViewById(R.id.call).setOnClickListener(v -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                    builder.setCancelable(false);
                    builder.setTitle("Confirmation");
                    builder.setMessage("Are you sure you want to call " + mFriends.
                            get(getAdapterPosition()).getmName() + "?");
                    builder.setPositiveButton("YES", (dialog, which) -> {
                        Intent callIntent = new Intent(Intent.ACTION_DIAL);
                        callIntent.setData(Uri.parse("tel:" + mFriends.
                                get(getAdapterPosition()).getmMobile()));
                        startActivity(callIntent);
                    });
                    builder.setNegativeButton("NO", (dialog, which) -> { });
                    builder.show();
                });
                itemView.findViewById(R.id.message).setOnClickListener(v -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                    builder.setCancelable(false);
                    builder.setTitle("Confirmation");
                    builder.setMessage("Are you sure you want to message " + mFriends.
                            get(getAdapterPosition()).getmName() + "?");
                    builder.setPositiveButton("YES", (dialog, which) -> {
                        Uri uri = Uri.parse("smsto:" + mFriends.get(getAdapterPosition()).
                                getmMobile());
                        Intent intent = new Intent(Intent.ACTION_SENDTO, uri);
                        intent.putExtra("sms_body", "Travel Mate - ");
                        startActivity(intent);
                    });
                    builder.setNegativeButton("NO", (dialog, which) -> { });
                    builder.show();
                });
            }
        }
    }

    class UserRecord {
        private String mName, mMobile;

        public UserRecord(String mName, String mMobile) {
            this.mName = mName;
            this.mMobile = mMobile;
        }

        public String getmName() {
            return mName;
        }

        public void setmName(String mName) {
            this.mName = mName;
        }

        public String getmMobile() {
            return mMobile;
        }

        public void setmMobile(String mMobile) {
            this.mMobile = mMobile;
        }
    }

}
