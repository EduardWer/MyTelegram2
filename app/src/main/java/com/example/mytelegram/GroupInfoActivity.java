package com.example.mytelegram;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GroupInfoActivity extends AppCompatActivity {
    private static final String TAG = "GroupInfoActivity";

    private String groupId;
    private String chatId;
    private String currentUserId;
    private String createdBy;
    private boolean isAdmin;
    private List<String> existingMemberIds;
    private String adminDomain; // Домен администратора

    private ImageView groupAvatar;
    private TextView groupName;
    private TextView groupDescription;
    private TextView groupMembersCount;
    private TextView memberCountBadge;
    private RecyclerView membersRecyclerView;
    private ProgressBar progressBar;
    private Button leaveGroupButton;
    private Button addMemberButton;

    private MembersAdapter membersAdapter;
    private List<MemberModel> membersList;

    private DatabaseReference groupRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_info);

        groupId = getIntent().getStringExtra("groupId");
        chatId = getIntent().getStringExtra("chatId");
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        existingMemberIds = new ArrayList<>();

        initViews();
        loadGroupInfo();
        loadMembers();
    }

    private void initViews() {
        groupAvatar = findViewById(R.id.groupAvatar);
        groupName = findViewById(R.id.groupName);
        groupDescription = findViewById(R.id.groupDescription);
        groupMembersCount = findViewById(R.id.groupMembersCount);
        memberCountBadge = findViewById(R.id.memberCountBadge);
        membersRecyclerView = findViewById(R.id.membersRecyclerView);
        progressBar = findViewById(R.id.progressBar);
        leaveGroupButton = findViewById(R.id.leaveGroupButton);
        addMemberButton = findViewById(R.id.addMemberButton);

        membersList = new ArrayList<>();
        membersAdapter = new MembersAdapter(membersList, currentUserId, this);
        membersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        membersRecyclerView.setAdapter(membersAdapter);

        leaveGroupButton.setOnClickListener(v -> leaveGroup());
        addMemberButton.setOnClickListener(v -> showAddMembersDialog());
    }

    private void loadGroupInfo() {
        progressBar.setVisibility(View.VISIBLE);
        groupRef = FirebaseDatabase.getInstance().getReference("groups").child(groupId);

        groupRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressBar.setVisibility(View.GONE);
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    String avatar = snapshot.child("avatarUrl").getValue(String.class);
                    String description = snapshot.child("description").getValue(String.class);
                    createdBy = snapshot.child("createdBy").getValue(String.class);

                    isAdmin = currentUserId.equals(createdBy);

                    if (name != null) groupName.setText(name);
                    if (description != null) groupDescription.setText(description);

                    if (avatar != null && !avatar.isEmpty()) {
                        Glide.with(GroupInfoActivity.this)
                                .load(avatar)
                                .placeholder(R.drawable.ic_person)
                                .circleCrop()
                                .into(groupAvatar);
                    }

                    // Загружаем домен администратора
                    if (createdBy != null) {
                        loadAdminDomain(createdBy);
                    }

                    // Показываем кнопки для админа
                    if (isAdmin) {
                        addMemberButton.setVisibility(View.VISIBLE);
                        leaveGroupButton.setText("Удалить группу");
                    } else {
                        addMemberButton.setVisibility(View.GONE);
                        leaveGroupButton.setText("Покинуть группу");
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Ошибка загрузки информации о группе: " + error.getMessage());
            }
        });
    }

    // Загрузка домена администратора
    private void loadAdminDomain(String adminId) {
        DatabaseReference adminRef = FirebaseDatabase.getInstance().getReference("users").child(adminId);
        adminRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String email = snapshot.child("email").getValue(String.class);
                    if (email != null && email.contains("@")) {
                        adminDomain = email.substring(email.indexOf("@") + 1).toLowerCase();
                        Log.d(TAG, "Admin domain: " + adminDomain);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // Получение домена пользователя по email
    private String getUserDomain(UserModel user) {
        if (user == null) return null;

        // Пытаемся получить email из данных пользователя
        String email = user.getEmail();
        if (email != null && email.contains("@")) {
            return email.substring(email.indexOf("@") + 1).toLowerCase();
        }

        // Если email нет в модели, загружаем из Firebase (асинхронно)
        // Но для фильтрации в адаптере используем кэш
        return null;
    }

    private void loadMembers() {
        groupRef = FirebaseDatabase.getInstance().getReference("groups").child(groupId);

        groupRef.child("members").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                membersList.clear();
                existingMemberIds.clear();

                for (DataSnapshot memberSnap : snapshot.getChildren()) {
                    String memberId = memberSnap.getKey();
                    Boolean isMember = memberSnap.getValue(Boolean.class);

                    if (memberId != null && isMember != null && isMember) {
                        existingMemberIds.add(memberId);

                        MemberModel member = new MemberModel();
                        member.setUid(memberId);
                        member.setRole(memberId.equals(createdBy) ? "Администратор" : "Участник");
                        membersList.add(member);

                        loadUserInfo(memberId, member);
                    }
                }

                updateMemberCount();
                membersAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Ошибка загрузки участников: " + error.getMessage());
            }
        });
    }

    private void loadUserInfo(String userId, MemberModel member) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String username = snapshot.child("username").getValue(String.class);
                    String email = snapshot.child("email").getValue(String.class);

                    if (username != null) {
                        member.setName(username);
                    }
                    if (email != null) {
                        member.setEmail(email);
                    }
                }
                loadAvatar(userId, member);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadAvatar(String userId, MemberModel member) {
        DatabaseReference avatarRef = FirebaseDatabase.getInstance().getReference("avatars").child(userId);
        avatarRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String avatarUrl = snapshot.getValue(String.class);
                    if (avatarUrl != null) {
                        member.setAvatarUrl(avatarUrl);
                    }
                }
                int position = findMemberPosition(member.getUid());
                if (position >= 0) {
                    membersAdapter.notifyItemChanged(position);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private int findMemberPosition(String uid) {
        for (int i = 0; i < membersList.size(); i++) {
            if (membersList.get(i).getUid().equals(uid)) {
                return i;
            }
        }
        return -1;
    }

    private void updateMemberCount() {
        int count = membersList.size();
        String countText = count + " участников";
        groupMembersCount.setText(countText);
        memberCountBadge.setText(String.valueOf(count));
    }

    // ==================== ДОБАВЛЕНИЕ УЧАСТНИКОВ ====================

    private void showAddMembersDialog() {
        if (TextUtils.isEmpty(adminDomain)) {
            // Пробуем загрузить домен админа синхронно
            loadAdminDomainAndShowDialog();
            return;
        }

        showAddMembersDialogWithDomain();
    }

    private void loadAdminDomainAndShowDialog() {
        if (createdBy == null) return;

        DatabaseReference adminRef = FirebaseDatabase.getInstance().getReference("users").child(createdBy);
        adminRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String email = snapshot.child("email").getValue(String.class);
                    if (email != null && email.contains("@")) {
                        adminDomain = email.substring(email.indexOf("@") + 1).toLowerCase();
                    }
                }
                showAddMembersDialogWithDomain();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showAddMembersDialogWithDomain();
            }
        });
    }

    private void showAddMembersDialogWithDomain() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_members, null);
        builder.setView(dialogView);

        String title = "Добавить участников";
        if (!TextUtils.isEmpty(adminDomain)) {
            title += " (домен: @" + adminDomain + ")";
        }
        builder.setTitle(title);

        AlertDialog dialog = builder.create();
        dialog.show();

        EditText searchEditText = dialogView.findViewById(R.id.searchUsersEditText);
        RecyclerView addUsersRecyclerView = dialogView.findViewById(R.id.addUsersRecyclerView);
        Button confirmAddButton = dialogView.findViewById(R.id.confirmAddButton);

        List<UserModel> allUsers = new ArrayList<>();
        List<UserModel> filteredUsers = new ArrayList<>();
        Map<String, String> userDomains = new HashMap<>(); // Кэш доменов

        AddMembersAdapter addAdapter = new AddMembersAdapter(filteredUsers, existingMemberIds, currentUserId, adminDomain, userDomains);

        addUsersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        addUsersRecyclerView.setAdapter(addAdapter);

        // Загружаем всех пользователей
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allUsers.clear();
                for (DataSnapshot userSnap : snapshot.getChildren()) {
                    UserModel user = userSnap.getValue(UserModel.class);
                    if (user != null) {
                        user.setUid(userSnap.getKey());

                        // Проверяем домен
                        String email = user.getEmail();
                        if (email != null && email.contains("@")) {
                            String userDomain = email.substring(email.indexOf("@") + 1).toLowerCase();
                            userDomains.put(user.getUid(), userDomain);
                        }

                        allUsers.add(user);
                        loadUserAvatar(user);
                    }
                }

                // Применяем фильтр по домену
                filterUsersByDomain(allUsers, filteredUsers, userDomains);
                addAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Поиск
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                filteredUsers.clear();
                String query = s.toString().toLowerCase();

                List<UserModel> sourceList = new ArrayList<>();
                filterUsersByDomain(allUsers, sourceList, userDomains);

                if (TextUtils.isEmpty(query)) {
                    filteredUsers.addAll(sourceList);
                } else {
                    for (UserModel user : sourceList) {
                        if (user.getUsername() != null && user.getUsername().toLowerCase().contains(query)) {
                            filteredUsers.add(user);
                        }
                    }
                }
                addAdapter.notifyDataSetChanged();
            }
        });

        // Кнопка подтверждения
        confirmAddButton.setOnClickListener(v -> {
            List<String> selectedIds = addAdapter.getSelectedUserIds();
            if (selectedIds.isEmpty()) {
                Toast.makeText(GroupInfoActivity.this, "Выберите хотя бы одного пользователя", Toast.LENGTH_SHORT).show();
                return;
            }

            // Проверяем домены выбранных пользователей
            if (!TextUtils.isEmpty(adminDomain)) {
                List<String> invalidUsers = new ArrayList<>();
                for (String uid : selectedIds) {
                    String userDomain = userDomains.get(uid);
                    if (userDomain != null && !userDomain.equals(adminDomain)) {
                        invalidUsers.add(uid);
                    }
                }

                if (!invalidUsers.isEmpty()) {
                    Toast.makeText(GroupInfoActivity.this,
                            "Нельзя добавить пользователей с другим доменом",
                            Toast.LENGTH_LONG).show();
                    return;
                }
            }

            addMembersToGroup(selectedIds);
            dialog.dismiss();
        });
    }

    // Фильтрация пользователей по домену
    private void filterUsersByDomain(List<UserModel> allUsers, List<UserModel> filtered, Map<String, String> userDomains) {
        filtered.clear();

        for (UserModel user : allUsers) {
            // Пропускаем текущего пользователя
            if (user.getUid().equals(currentUserId)) {
                continue;
            }

            // Если домен админа не задан, показываем всех
            if (TextUtils.isEmpty(adminDomain)) {
                filtered.add(user);
                continue;
            }

            // Проверяем домен пользователя
            String userDomain = userDomains.get(user.getUid());
            if (userDomain == null) {
                String email = user.getEmail();
                if (email != null && email.contains("@")) {
                    userDomain = email.substring(email.indexOf("@") + 1).toLowerCase();
                    userDomains.put(user.getUid(), userDomain);
                }
            }

            // Добавляем только пользователей с тем же доменом
            if (userDomain != null && userDomain.equals(adminDomain)) {
                filtered.add(user);
            }
        }
    }

    private void loadUserAvatar(UserModel user) {
        FirebaseDatabase.getInstance().getReference("avatars")
                .child(user.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            user.setAvatarUrl(snapshot.getValue(String.class));
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void addMembersToGroup(List<String> newMemberIds) {
        progressBar.setVisibility(View.VISIBLE);

        Map<String, Object> updates = new HashMap<>();
        long timestamp = System.currentTimeMillis();
        String groupTitle = groupName.getText().toString();

        for (String uid : newMemberIds) {
            // Добавляем в группу
            updates.put("groups/" + groupId + "/members/" + uid, true);

            // Добавляем чат пользователю
            Map<String, Object> chatEntry = new HashMap<>();
            chatEntry.put("chatId", chatId);
            chatEntry.put("chatType", "group");
            chatEntry.put("groupId", groupId);
            chatEntry.put("groupName", groupTitle);
            chatEntry.put("lastMessage", "👤 Добавлен в группу");
            chatEntry.put("timestamp", timestamp);
            chatEntry.put("unreadCount", 1);
            chatEntry.put("lastMessageSenderId", currentUserId);
            chatEntry.put("messageType", "system");

            updates.put("userChats/" + uid + "/" + chatId, chatEntry);

            // Системное сообщение в чат
            String msgId = FirebaseDatabase.getInstance().getReference()
                    .child("chats").child(chatId).child("messages").push().getKey();
            if (msgId != null) {
                Map<String, Object> sysMsg = new HashMap<>();
                sysMsg.put("id", msgId);
                sysMsg.put("text", "👤 Новый участник добавлен в группу");
                sysMsg.put("senderId", "system");
                sysMsg.put("timestamp", timestamp);
                sysMsg.put("chatId", chatId);
                sysMsg.put("chatType", "group");
                sysMsg.put("messageType", "system");
                sysMsg.put("isRead", false);
                sysMsg.put("edited", false);
                updates.put("chats/" + chatId + "/messages/" + msgId, sysMsg);
            }
        }

        FirebaseDatabase.getInstance().getReference().updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Добавлено участников: " + newMemberIds.size(), Toast.LENGTH_SHORT).show();
                    loadMembers();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // ==================== УДАЛЕНИЕ ГРУППЫ / ВЫХОД ====================

    private void leaveGroup() {
        if (isAdmin) {
            new AlertDialog.Builder(this)
                    .setTitle("Удалить группу")
                    .setMessage("Вы уверены, что хотите удалить эту группу?")
                    .setPositiveButton("Удалить", (dialog, which) -> deleteGroup())
                    .setNegativeButton("Отмена", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Покинуть группу")
                    .setMessage("Вы уверены, что хотите покинуть эту группу?")
                    .setPositiveButton("Покинуть", (dialog, which) -> removeCurrentUser())
                    .setNegativeButton("Отмена", null)
                    .show();
        }
    }

    private void removeCurrentUser() {
        progressBar.setVisibility(View.VISIBLE);

        Map<String, Object> updates = new HashMap<>();
        updates.put("groups/" + groupId + "/members/" + currentUserId, false);
        updates.put("userChats/" + currentUserId + "/" + chatId, null);

        FirebaseDatabase.getInstance().getReference().updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Вы покинули группу", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void deleteGroup() {
        progressBar.setVisibility(View.VISIBLE);

        Map<String, Object> updates = new HashMap<>();
        updates.put("groups/" + groupId, null);
        updates.put("chats/" + chatId, null);

        groupRef.child("members").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot memberSnap : snapshot.getChildren()) {
                    String memberId = memberSnap.getKey();
                    if (memberId != null) {
                        updates.put("userChats/" + memberId + "/" + chatId, null);
                    }
                }

                FirebaseDatabase.getInstance().getReference().updateChildren(updates)
                        .addOnSuccessListener(aVoid -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(GroupInfoActivity.this, "Группа удалена", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(GroupInfoActivity.this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    public void removeMember(String memberId) {
        if (!isAdmin) return;

        new AlertDialog.Builder(this)
                .setTitle("Удалить участника")
                .setMessage("Вы уверены, что хотите удалить этого участника из группы?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    progressBar.setVisibility(View.VISIBLE);

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("groups/" + groupId + "/members/" + memberId, false);
                    updates.put("userChats/" + memberId + "/" + chatId, null);

                    FirebaseDatabase.getInstance().getReference().updateChildren(updates)
                            .addOnSuccessListener(aVoid -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(this, "Участник удален", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ==================== АДАПТЕР УЧАСТНИКОВ ====================

    private class MembersAdapter extends RecyclerView.Adapter<MembersAdapter.ViewHolder> {

        private List<MemberModel> members;
        private String currentUserId;
        private GroupInfoActivity activity;

        public MembersAdapter(List<MemberModel> members, String currentUserId, GroupInfoActivity activity) {
            this.members = members;
            this.currentUserId = currentUserId;
            this.activity = activity;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_group_member, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            MemberModel member = members.get(position);

            holder.memberName.setText(member.getName() != null ? member.getName() : "Пользователь");
            holder.memberRole.setText(member.getRole() != null ? member.getRole() : "Участник");

            // Показываем email/домен если есть
            if (member.getEmail() != null && isAdmin) {
                holder.memberRole.setText(member.getRole() + " • " + member.getEmail());
            }

            if (member.getAvatarUrl() != null && !member.getAvatarUrl().isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(member.getAvatarUrl())
                        .placeholder(R.drawable.ic_person)
                        .circleCrop()
                        .into(holder.memberAvatar);
            } else {
                holder.memberAvatar.setImageResource(R.drawable.ic_person);
            }

            if (isAdmin && !member.getUid().equals(currentUserId)) {
                holder.removeButton.setVisibility(View.VISIBLE);
                holder.removeButton.setOnClickListener(v -> activity.removeMember(member.getUid()));
            } else {
                holder.removeButton.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return members.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView memberAvatar;
            TextView memberName;
            TextView memberRole;
            ImageButton removeButton;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                memberAvatar = itemView.findViewById(R.id.memberAvatar);
                memberName = itemView.findViewById(R.id.memberName);
                memberRole = itemView.findViewById(R.id.memberRole);
                removeButton = itemView.findViewById(R.id.removeMemberButton);
            }
        }
    }

    // ==================== АДАПТЕР ДЛЯ ДОБАВЛЕНИЯ ====================

    private static class AddMembersAdapter extends RecyclerView.Adapter<AddMembersAdapter.ViewHolder> {

        private List<UserModel> users;
        private Set<String> selectedIds;
        private Set<String> existingIds;
        private String currentUserId;
        private String adminDomain;
        private Map<String, String> userDomains;

        public AddMembersAdapter(List<UserModel> users, List<String> existingIds,
                                 String currentUserId, String adminDomain,
                                 Map<String, String> userDomains) {
            this.users = users;
            this.selectedIds = new HashSet<>();
            this.existingIds = new HashSet<>(existingIds);
            this.currentUserId = currentUserId;
            this.adminDomain = adminDomain;
            this.userDomains = userDomains;
        }

        public List<String> getSelectedUserIds() {
            return new ArrayList<>(selectedIds);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_user_selection, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            UserModel user = users.get(position);
            boolean isExisting = existingIds.contains(user.getUid());
            boolean isCurrentUser = user.getUid().equals(currentUserId);

            // Проверяем домен
            String userDomain = userDomains.get(user.getUid());
            boolean sameDomain = TextUtils.isEmpty(adminDomain) ||
                    (userDomain != null && userDomain.equals(adminDomain));

            holder.nameText.setText(user.getUsername() != null ? user.getUsername() : "Пользователь");

            // Показываем информацию о домене
            if (!TextUtils.isEmpty(userDomain)) {
                holder.statusText.setText("@" + userDomain);
                holder.statusText.setVisibility(View.VISIBLE);
            } else {
                holder.statusText.setVisibility(View.GONE);
            }

            if (isCurrentUser) {
                holder.checkBox.setChecked(true);
                holder.checkBox.setEnabled(false);
                holder.nameText.setText(holder.nameText.getText() + " (вы)");
                holder.statusText.setText("Администратор");
            } else if (isExisting) {
                holder.checkBox.setChecked(true);
                holder.checkBox.setEnabled(false);
                holder.nameText.setText(holder.nameText.getText() + " (в группе)");
            } else if (!sameDomain) {
                // Другой домен - нельзя выбрать
                holder.checkBox.setChecked(false);
                holder.checkBox.setEnabled(false);
                holder.nameText.setText(holder.nameText.getText() + " ⛔ другой домен");
                holder.itemView.setAlpha(0.5f);
            } else {
                holder.checkBox.setChecked(selectedIds.contains(user.getUid()));
                holder.checkBox.setEnabled(true);
                holder.itemView.setAlpha(1.0f);
            }

            holder.itemView.setOnClickListener(v -> {
                if (!isExisting && !isCurrentUser && sameDomain) {
                    String uid = user.getUid();
                    if (selectedIds.contains(uid)) {
                        selectedIds.remove(uid);
                    } else {
                        selectedIds.add(uid);
                    }
                    notifyItemChanged(position);
                } else if (!sameDomain) {
                    Toast.makeText(holder.itemView.getContext(),
                            "Можно добавить только пользователей с доменом @" + adminDomain,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return users.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText;
            TextView statusText;
            CheckBox checkBox;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.userNameTextView);
                statusText = itemView.findViewById(R.id.userStatusTextView);
                checkBox = itemView.findViewById(R.id.userCheckBox);
            }
        }
    }

    // ==================== МОДЕЛИ ====================

    private static class MemberModel {
        private String uid;
        private String name;
        private String role;
        private String avatarUrl;
        private String email;

        public String getUid() { return uid; }
        public void setUid(String uid) { this.uid = uid; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
}