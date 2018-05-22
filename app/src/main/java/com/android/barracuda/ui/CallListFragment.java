package com.android.barracuda.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.barracuda.R;
import com.android.barracuda.data.CallDB;
import com.android.barracuda.data.StaticConfig;
import com.android.barracuda.model.Call;
import com.android.barracuda.model.FileModel;
import com.android.barracuda.model.ListCall;
import com.android.barracuda.service.ServiceUtils;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.yarolegovich.lovelydialog.LovelyInfoDialog;
import com.yarolegovich.lovelydialog.LovelyProgressDialog;
import com.yarolegovich.lovelydialog.LovelyTextInputDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.hdodenhof.circleimageview.CircleImageView;

public class CallListFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

  private RecyclerView recyclerListFrends;
  private ListCallAdapter adapter;
  public FragFriendClickFloatButton onClickFloatButton;
  private ListCall dataListFriend = null;
  private ArrayList<String> listFriendID = null;
  private LovelyProgressDialog dialogFindAllFriend;
  private SwipeRefreshLayout mSwipeRefreshLayout;
  private CountDownTimer detectFriendOnline;
  public static int ACTION_START_CHAT = 1;

  public static final String ACTION_DELETE_FRIEND = "com.android.rivchat.DELETE_FRIEND";

  private BroadcastReceiver deleteFriendReceiver;

  public CallListFragment() {
    onClickFloatButton = new FragFriendClickFloatButton();
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(final LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    detectFriendOnline = new CountDownTimer(System.currentTimeMillis(), StaticConfig.TIME_TO_REFRESH) {
      @Override
      public void onTick(long l) {
        ServiceUtils.updateCallStatus(getContext(), dataListFriend);
        ServiceUtils.updateUserStatus(getContext());
      }

      @Override
      public void onFinish() {

      }
    };
    if (dataListFriend == null) {
      dataListFriend = CallDB.getInstance(getContext()).getListCall();
      if (dataListFriend.getListCall().size() > 0) {
        listFriendID = new ArrayList<>();
        for (Call call : dataListFriend.getListCall()) {
          listFriendID.add(call.id);
        }
        detectFriendOnline.start();
      }
    }
    View layout = inflater.inflate(R.layout.fragment_people, container, false);
    LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
    recyclerListFrends = (RecyclerView) layout.findViewById(R.id.recycleListFriend);
    recyclerListFrends.setLayoutManager(linearLayoutManager);
    mSwipeRefreshLayout = (SwipeRefreshLayout) layout.findViewById(R.id.swipeRefreshLayout);
    mSwipeRefreshLayout.setOnRefreshListener(this);
    adapter = new ListCallAdapter(getContext(), dataListFriend, this);
    recyclerListFrends.setAdapter(adapter);
    dialogFindAllFriend = new LovelyProgressDialog(getContext());
    if (listFriendID == null) {
      listFriendID = new ArrayList<>();
      dialogFindAllFriend.setCancelable(false)
        .setIcon(R.drawable.ic_add_friend)
        .setTitle("Get all friend....")
        .setTopColorRes(R.color.colorPrimary)
        .show();
      getListCall();
    }

    deleteFriendReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String idDeleted = intent.getExtras().getString("idFriend");
        for (Call call : dataListFriend.getListCall()) {
          if (idDeleted.equals(call.id)) {
            ArrayList<Call> calls = dataListFriend.getListCall();
            calls.remove(call);
            break;
          }
        }
        adapter.notifyDataSetChanged();
      }
    };

    IntentFilter intentFilter = new IntentFilter(ACTION_DELETE_FRIEND);
    getContext().registerReceiver(deleteFriendReceiver, intentFilter);

    return layout;
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();

    getContext().unregisterReceiver(deleteFriendReceiver);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (ACTION_START_CHAT == requestCode && data != null && ListFriendsAdapter.mapMark != null) {
      ListFriendsAdapter.mapMark.put(data.getStringExtra("idFriend"), false);
    }
  }

  @Override
  public void onRefresh() {
    listFriendID.clear();
    dataListFriend.getListCall().clear();
    adapter.notifyDataSetChanged();
    CallDB.getInstance(getContext()).dropDB();
    detectFriendOnline.cancel();
    getListCall();
  }

  public class FragFriendClickFloatButton implements View.OnClickListener {
    Context context;
    LovelyProgressDialog dialogWait;

    public FragFriendClickFloatButton() {
    }

    public FragFriendClickFloatButton getInstance(Context context) {
      this.context = context;
      dialogWait = new LovelyProgressDialog(context);
      return this;
    }

    @Override
    public void onClick(final View view) {

      new LovelyTextInputDialog(view.getContext(), R.style.EditTextTintTheme)
        .setTopColorRes(R.color.colorPrimary)
        .setTitle("Add friend")
        .setMessage("Enter friend's phone number")
        .setIcon(R.drawable.ic_add_friend)
        .setInputType(InputType.TYPE_CLASS_PHONE)
        .setInputFilter("Phone number not found", new LovelyTextInputDialog.TextFilter() {
          @Override
          public boolean check(String text) {
            Pattern VALID_EMAIL_ADDRESS_REGEX =
              Pattern.compile("\\d+", Pattern.CASE_INSENSITIVE);
            Matcher matcher = VALID_EMAIL_ADDRESS_REGEX.matcher(text);
            return matcher.find();
          }
        })
        .setConfirmButton(android.R.string.ok, new LovelyTextInputDialog.OnTextInputConfirmListener() {
          @Override
          public void onTextInputConfirmed(String text) {
            //Tim id user id
            findIDPhoneNumber(text);
            //Check xem da ton tai ban ghi friend chua
            //Ghi them 1 ban ghi
          }
        })
        .show();
    }

    private void findIDPhoneNumber(String phoneNumber) {
      dialogWait.setCancelable(false)
        .setIcon(R.drawable.ic_add_friend)
        .setTitle("Finding friend....")
        .setTopColorRes(R.color.colorPrimary)
        .show();
      FirebaseDatabase.getInstance().getReference().child("user").orderByChild("phoneNumber").equalTo(phoneNumber).addListenerForSingleValueEvent(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
          dialogWait.dismiss();
          if (dataSnapshot.getValue() == null) {
            //phoneNumber not found
            new LovelyInfoDialog(context)
              .setTopColorRes(R.color.colorAccent)
              .setIcon(R.drawable.ic_add_friend)
              .setTitle("Fail")
              .setMessage("phoneNumber not found")
              .show();
          } else {
            String id = ((HashMap) dataSnapshot.getValue()).keySet().iterator().next().toString();
            if (id.equals(StaticConfig.UID)) {
              new LovelyInfoDialog(context)
                .setTopColorRes(R.color.colorAccent)
                .setIcon(R.drawable.ic_add_friend)
                .setTitle("Fail")
                .setMessage("Phone number not valid")
                .show();
            } else {
              HashMap userMap = (HashMap) ((HashMap) dataSnapshot.getValue()).get(id);
              Call call = new Call();
              call.name = (String) userMap.get("name");
              call.phoneNumber = (String) userMap.get("phoneNumber");
              call.avata = (String) userMap.get("avata");
              call.id = id;
              call.idRoom = id.compareTo(StaticConfig.UID) > 0 ? (StaticConfig.UID + id).hashCode() + "" : "" + (id + StaticConfig.UID).hashCode();
              checkBeforAddFriend(id, call);
            }
          }
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

        }
      });
    }

    /**
     * Lay danh sach friend cua một UID
     */
    private void checkBeforAddFriend(final String idFriend, Call callInfo) {
      dialogWait.setCancelable(false)
        .setIcon(R.drawable.ic_add_friend)
        .setTitle("Add friend....")
        .setTopColorRes(R.color.colorPrimary)
        .show();

      //Check xem da ton tai id trong danh sach id chua
      if (listFriendID.contains(idFriend)) {
        dialogWait.dismiss();
        new LovelyInfoDialog(context)
          .setTopColorRes(R.color.colorPrimary)
          .setIcon(R.drawable.ic_add_friend)
          .setTitle("Friend")
          .setMessage("User " + callInfo.phoneNumber + " has been friend")
          .show();
      } else {
        addFriend(idFriend, true);
        listFriendID.add(idFriend);
        dataListFriend.getListCall().add(callInfo);
        CallDB.getInstance(getContext()).addCall(callInfo);
        adapter.notifyDataSetChanged();
      }
    }

    /**
     * Add friend
     *
     * @param idFriend
     */
    private void addFriend(final String idFriend, boolean isIdFriend) {
      if (idFriend != null) {
        if (isIdFriend) {
          FirebaseDatabase.getInstance().getReference().child("friend/" + StaticConfig.UID).push().setValue(idFriend)
            .addOnCompleteListener(new OnCompleteListener<Void>() {
              @Override
              public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                  addFriend(idFriend, false);
                }
              }
            })
            .addOnFailureListener(new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {
                dialogWait.dismiss();
                new LovelyInfoDialog(context)
                  .setTopColorRes(R.color.colorAccent)
                  .setIcon(R.drawable.ic_add_friend)
                  .setTitle("False")
                  .setMessage("False to add friend success")
                  .show();
              }
            });
        } else {
          FirebaseDatabase.getInstance().getReference().child("friend/" + idFriend).push().setValue(StaticConfig.UID).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
              if (task.isSuccessful()) {
                addFriend(null, false);
              }
            }
          })
            .addOnFailureListener(new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {
                dialogWait.dismiss();
                new LovelyInfoDialog(context)
                  .setTopColorRes(R.color.colorAccent)
                  .setIcon(R.drawable.ic_add_friend)
                  .setTitle("False")
                  .setMessage("False to add friend success")
                  .show();
              }
            });
        }
      } else {
        dialogWait.dismiss();
        new LovelyInfoDialog(context)
          .setTopColorRes(R.color.colorPrimary)
          .setIcon(R.drawable.ic_add_friend)
          .setTitle("Success")
          .setMessage("Add friend success")
          .show();
      }
    }


  }

  /**
   * Lay danh sach ban be tren server
   */
  private void getListCall()

  {
    FirebaseDatabase.getInstance().getReference().child("friend/" + StaticConfig.UID).addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        if (dataSnapshot.getValue() != null) {
          HashMap mapRecord = (HashMap) dataSnapshot.getValue();
          Iterator listKey = mapRecord.keySet().iterator();
          while (listKey.hasNext()) {
            String key = listKey.next().toString();
            listFriendID.add(mapRecord.get(key).toString());
          }
          getAllCallInfo(0);
        } else {
          dialogFindAllFriend.dismiss();
        }
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {
      }
    });
  }

  private void getAllCallInfo(final int index) {
    if (index == listFriendID.size()) {
      //save list friend
      adapter.notifyDataSetChanged();
      dialogFindAllFriend.dismiss();
      mSwipeRefreshLayout.setRefreshing(false);
      detectFriendOnline.start();
    } else {
      final String id = listFriendID.get(index);
      FirebaseDatabase.getInstance().getReference().child("user/" + id).addListenerForSingleValueEvent(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
          if (dataSnapshot.getValue() != null) {
            Call call = new Call();
            HashMap mapUserInfo = (HashMap) dataSnapshot.getValue();
            call.name = (String) mapUserInfo.get("name");
            call.phoneNumber = (String) mapUserInfo.get("phoneNumber");
            call.avata = (String) mapUserInfo.get("avata");
            call.id = id;
            call.idRoom = id.compareTo(StaticConfig.UID) > 0 ? (StaticConfig.UID + id).hashCode() + "" : "" + (id + StaticConfig.UID).hashCode();
            dataListFriend.getListCall().add(call);
            CallDB.getInstance(getContext()).addCall(call);
          }
          getAllCallInfo(index + 1);
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

        }
      });
    }
  }
}

class ListCallAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private ListCall listCall;
  private Context context;
  public static Map<String, Query> mapQuery;
  public static Map<String, DatabaseReference> mapQueryOnline;
  public static Map<String, ChildEventListener> mapChildListener;
  public static Map<String, ChildEventListener> mapChildListenerOnline;
  public static Map<String, Boolean> mapMark;
  private CallListFragment fragment;
  LovelyProgressDialog dialogWaitDeleting;

  public ListCallAdapter(Context context, ListCall listCall, CallListFragment fragment) {
    this.listCall = listCall;
    this.context = context;
    mapQuery = new HashMap<>();
    mapChildListener = new HashMap<>();
    mapMark = new HashMap<>();
    mapChildListenerOnline = new HashMap<>();
    mapQueryOnline = new HashMap<>();
    this.fragment = fragment;
    dialogWaitDeleting = new LovelyProgressDialog(context);
  }

  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(context).inflate(R.layout.rc_item_friend, parent, false);
    return new ItemFriendViewHolder(context, view);
  }

  @Override
  public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
    final String name = listCall.getListCall().get(position).name;
    final String id = listCall.getListCall().get(position).id;
    final String idRoom = listCall.getListCall().get(position).idRoom;
    final String avata = listCall.getListCall().get(position).avata;
    final String type = listCall.getListCall().get(position).type;
    ((ItemFriendViewHolder) holder).txtName.setText(name);

    ((View) ((ItemFriendViewHolder) holder).txtName.getParent().getParent().getParent())
      .setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          ((ItemFriendViewHolder) holder).txtMessage.setTypeface(Typeface.DEFAULT);
          ((ItemFriendViewHolder) holder).txtName.setTypeface(Typeface.DEFAULT);
          Intent intent = new Intent(context, ChatActivity.class);
          intent.putExtra(StaticConfig.INTENT_KEY_CHAT_FRIEND, name);
          ArrayList<CharSequence> idFriend = new ArrayList<CharSequence>();
          idFriend.add(id);
          intent.putCharSequenceArrayListExtra(StaticConfig.INTENT_KEY_CHAT_ID, idFriend);
          intent.putExtra(StaticConfig.INTENT_KEY_CHAT_ROOM_ID, idRoom);
          ChatActivity.bitmapAvataFriend = new HashMap<>();
          if (!avata.equals(StaticConfig.STR_DEFAULT_BASE64)) {
            byte[] decodedString = Base64.decode(avata, Base64.DEFAULT);
            ChatActivity.bitmapAvataFriend.put(id, BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length));
          } else {
            ChatActivity.bitmapAvataFriend.put(id, BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avata));
          }

          mapMark.put(id, null);
          fragment.startActivityForResult(intent, FriendsFragment.ACTION_START_CHAT);
        }
      });

    //nhấn giữ để xóa bạn
    ((View) ((ItemFriendViewHolder) holder).txtName.getParent().getParent().getParent())
      .setOnLongClickListener(new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
          String friendName = (String) ((ItemFriendViewHolder) holder).txtName.getText();

          new AlertDialog.Builder(context)
            .setTitle("Delete Friend")
            .setMessage("Are you sure want to delete " + friendName + "?")
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                final String idFriendRemoval = listCall.getListCall().get(position).id;
                dialogWaitDeleting.setTitle("Deleting...")
                  .setCancelable(false)
                  .setTopColorRes(R.color.colorAccent)
                  .show();
                deleteFriend(idFriendRemoval);
              }
            })
            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
              }
            }).show();

          return true;
        }
      });


    if (listCall.getListCall().get(position).message.text != null && listCall.getListCall().get(position).message.text.length() > 0) {
      ((ItemFriendViewHolder) holder).txtMessage.setVisibility(View.VISIBLE);
      ((ItemFriendViewHolder) holder).txtTime.setVisibility(View.VISIBLE);
      if (!listCall.getListCall().get(position).message.text.startsWith(id)) {
        ((ItemFriendViewHolder) holder).txtMessage.setText(listCall.getListCall().get(position).message.text);
        ((ItemFriendViewHolder) holder).txtMessage.setTypeface(Typeface.DEFAULT);
        ((ItemFriendViewHolder) holder).txtName.setTypeface(Typeface.DEFAULT);
      } else {
        ((ItemFriendViewHolder) holder).txtMessage.setText(listCall.getListCall().get(position).message.text.substring((id + "").length()));
        ((ItemFriendViewHolder) holder).txtMessage.setTypeface(Typeface.DEFAULT_BOLD);
        ((ItemFriendViewHolder) holder).txtName.setTypeface(Typeface.DEFAULT_BOLD);
      }
      String time = new SimpleDateFormat("EEE, d MMM yyyy").format(new Date(listCall.getListCall().get(position).message.timestamp));
      String today = new SimpleDateFormat("EEE, d MMM yyyy").format(new Date(System.currentTimeMillis()));
      if (today.equals(time)) {
        ((ItemFriendViewHolder) holder).txtTime.setText(new SimpleDateFormat("HH:mm").format(new Date(listCall.getListCall().get(position).message.timestamp)));
      } else {
        ((ItemFriendViewHolder) holder).txtTime.setText(new SimpleDateFormat("MMM d").format(new Date(listCall.getListCall().get(position).message.timestamp)));
      }
    } else {
      ((ItemFriendViewHolder) holder).txtMessage.setVisibility(View.GONE);
      ((ItemFriendViewHolder) holder).txtTime.setVisibility(View.GONE);
      if (mapQuery.get(id) == null && mapChildListener.get(id) == null) {
        mapQuery.put(id, FirebaseDatabase.getInstance().getReference().child("message/" + idRoom).limitToLast(1));
        mapChildListener.put(id, new ChildEventListener() {
          @Override
          public void onChildAdded(DataSnapshot dataSnapshot, String s) {
            HashMap mapMessage = (HashMap) dataSnapshot.getValue();

            if (listCall.getListCall().get(position).message != null &&
              listCall.getListCall().get(position).message.text != null &&
              listCall.getListCall() != null) {
              if (mapMark.get(id) != null) {
                if (!mapMark.get(id)) {
                  listCall.getListCall().get(position).message.text = id + mapMessage.get("text");
                } else {
                  listCall.getListCall().get(position).message.text = (String) mapMessage.get("text");
                }
                notifyDataSetChanged();
                mapMark.put(id, false);
              } else {
                listCall.getListCall().get(position).message.text = (String) mapMessage.get("text");
                notifyDataSetChanged();
              }
            }

            if (listCall.getListCall().get(position).message != null &&
              listCall.getListCall().get(position).message.fileModel != null &&
              listCall.getListCall() != null) {

              listCall.getListCall().get(position).message.fileModel = (FileModel) mapMessage.get("fileModel");
              notifyDataSetChanged();

            }

            //TODO for fileModel

            listCall.getListCall().get(position).message.timestamp = (long) mapMessage.get("timestamp");
          }

          @Override
          public void onChildChanged(DataSnapshot dataSnapshot, String s) {

          }

          @Override
          public void onChildRemoved(DataSnapshot dataSnapshot) {

          }

          @Override
          public void onChildMoved(DataSnapshot dataSnapshot, String s) {

          }

          @Override
          public void onCancelled(DatabaseError databaseError) {

          }
        });
        mapQuery.get(id).addChildEventListener(mapChildListener.get(id));
        mapMark.put(id, true);
      } else {
        mapQuery.get(id).removeEventListener(mapChildListener.get(id));
        mapQuery.get(id).addChildEventListener(mapChildListener.get(id));
        mapMark.put(id, true);
      }
    }
    if (StaticConfig.STR_DEFAULT_BASE64.equals(listCall.getListCall().get(position).avata)) {
      ((ItemFriendViewHolder) holder).avata.setImageResource(R.drawable.default_avata);
    } else {

      if (listCall.getListCall().get(position).avata != null) {
        byte[] decodedString = Base64.decode(listCall.getListCall().get(position).avata, Base64.DEFAULT);
        Bitmap src = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        ((ItemFriendViewHolder) holder).avata.setImageBitmap(src);
      }
    }


    if (mapQueryOnline.get(id) == null && mapChildListenerOnline.get(id) == null) {
      mapQueryOnline.put(id, FirebaseDatabase.getInstance().getReference().child("user/" + id + "/status"));
      mapChildListenerOnline.put(id, new ChildEventListener() {
        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String s) {
          if (dataSnapshot.getValue() != null && dataSnapshot.getKey().equals("isOnline")) {
            Log.d("FriendsFragment add " + id, (boolean) dataSnapshot.getValue() + "");
            listCall.getListCall().get(position).status.isOnline = (boolean) dataSnapshot.getValue();
            notifyDataSetChanged();
          }
        }

        @Override
        public void onChildChanged(DataSnapshot dataSnapshot, String s) {
          if (dataSnapshot.getValue() != null && dataSnapshot.getKey().equals("isOnline")) {
            Log.d("FriendsFragment change " + id, (boolean) dataSnapshot.getValue() + "");
            listCall.getListCall().get(position).status.isOnline = (boolean) dataSnapshot.getValue();
            notifyDataSetChanged();
          }
        }

        @Override
        public void onChildRemoved(DataSnapshot dataSnapshot) {

        }

        @Override
        public void onChildMoved(DataSnapshot dataSnapshot, String s) {

        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

        }
      });
      mapQueryOnline.get(id).addChildEventListener(mapChildListenerOnline.get(id));
    }

    if (listCall.getListCall().get(position).status.isOnline) {
      ((ItemFriendViewHolder) holder).avata.setBorderWidth(10);
    } else {
      ((ItemFriendViewHolder) holder).avata.setBorderWidth(0);
    }
  }

  @Override
  public int getItemCount() {
    return listCall.getListCall() != null ? listCall.getListCall().size() : 0;
  }

  /**
   * Delete friend
   *
   * @param idFriend
   */
  private void deleteFriend(final String idFriend) {
    if (idFriend != null) {
      FirebaseDatabase.getInstance().getReference().child("friend").child(StaticConfig.UID)
        .orderByValue().equalTo(idFriend).addListenerForSingleValueEvent(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {

          if (dataSnapshot.getValue() == null) {
            //phoneNumber not found
            dialogWaitDeleting.dismiss();
            new LovelyInfoDialog(context)
              .setTopColorRes(R.color.colorAccent)
              .setTitle("Error")
              .setMessage("Error occurred during deleting friend")
              .show();
          } else {
            String idRemoval = ((HashMap) dataSnapshot.getValue()).keySet().iterator().next().toString();
            FirebaseDatabase.getInstance().getReference().child("friend")
              .child(StaticConfig.UID).child(idRemoval).removeValue()
              .addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                  dialogWaitDeleting.dismiss();

                  new LovelyInfoDialog(context)
                    .setTopColorRes(R.color.colorAccent)
                    .setTitle("Success")
                    .setMessage("Friend deleting successfully")
                    .show();

                  Intent intentDeleted = new Intent(FriendsFragment.ACTION_DELETE_FRIEND);
                  intentDeleted.putExtra("idFriend", idFriend);
                  context.sendBroadcast(intentDeleted);
                }
              })
              .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                  dialogWaitDeleting.dismiss();
                  new LovelyInfoDialog(context)
                    .setTopColorRes(R.color.colorAccent)
                    .setTitle("Error")
                    .setMessage("Error occurred during deleting friend")
                    .show();
                }
              });
          }
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {

        }
      });
    } else {
      dialogWaitDeleting.dismiss();
      new LovelyInfoDialog(context)
        .setTopColorRes(R.color.colorPrimary)
        .setTitle("Error")
        .setMessage("Error occurred during deleting friend")
        .show();
    }
  }
}

class ItemCallViewHolder extends RecyclerView.ViewHolder {
  public CircleImageView avata;
  public TextView txtName, txtTime, txtMessage;
  private Context context;

  ItemCallViewHolder(Context context, View itemView) {
    super(itemView);
    avata = (CircleImageView) itemView.findViewById(R.id.icon_avata);
    txtName = (TextView) itemView.findViewById(R.id.txtName);
    txtTime = (TextView) itemView.findViewById(R.id.txtTime);
    txtMessage = (TextView) itemView.findViewById(R.id.txtMessage);
    this.context = context;
  }
}
