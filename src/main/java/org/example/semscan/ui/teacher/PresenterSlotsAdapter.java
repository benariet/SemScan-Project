package org.example.semscan.ui.teacher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import org.example.semscan.R;
import org.example.semscan.data.api.ApiService;
import org.example.semscan.utils.Logger;
import org.example.semscan.utils.PreferencesManager;
import org.example.semscan.utils.ServerLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class PresenterSlotsAdapter extends RecyclerView.Adapter<PresenterSlotsAdapter.SlotViewHolder> {

    interface SlotActionListener {
        void onRegisterClicked(ApiService.SlotCard slot);
        void onJoinWaitingList(ApiService.SlotCard slot);
        void onCancelWaitingList(ApiService.SlotCard slot);
        void onSlotClicked(ApiService.SlotCard slot, boolean isFull);
    }

    private final List<ApiService.SlotCard> items = new ArrayList<>();
    private final SlotActionListener listener;

    PresenterSlotsAdapter(@NonNull SlotActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public SlotViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_presenter_slot, parent, false);
        return new SlotViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SlotViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    void submitList(List<ApiService.SlotCard> slots) {
        items.clear();
        if (slots != null) {
            items.addAll(slots);
        }
        notifyDataSetChanged();
    }

    private String formatPresenters(List<ApiService.PresenterCoPresenter> presenters) {
        if (presenters == null || presenters.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (ApiService.PresenterCoPresenter presenter : presenters) {
            StringBuilder builder = new StringBuilder();
            if (presenter.name != null) {
                builder.append(presenter.name);
            }
            if (presenter.topic != null && presenter.topic.trim().length() > 0) {
                if (builder.length() > 0) {
                    builder.append(" — ");
                }
                builder.append(presenter.topic.trim());
            }
            if (builder.length() > 0) {
                names.add(builder.toString());
            }
        }
        if (names.isEmpty()) {
            return null;
        }
        return android.text.TextUtils.join("\n", names);
    }
    
    /**
     * Format names from registered list for display (e.g., "Name1, Name2")
     * Returns null if no names available
     */
    private String formatNamesForDisplay(List<ApiService.PresenterCoPresenter> registered, int maxCount) {
        if (registered == null || registered.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        int count = Math.min(maxCount, registered.size());
        for (int i = 0; i < count; i++) {
            ApiService.PresenterCoPresenter presenter = registered.get(i);
            if (presenter != null && presenter.name != null && !presenter.name.trim().isEmpty()) {
                names.add(presenter.name.trim());
            }
        }
        if (names.isEmpty()) {
            return null;
        }
        return TextUtils.join(", ", names);
    }

    class SlotViewHolder extends RecyclerView.ViewHolder {

        private final TextView title;
        private final TextView statusText;
        private final LinearLayout layoutSlotContent;
        private final MaterialButton registerButton;
        private final MaterialButton waitingListButton;
        private final MaterialButton cancelWaitingListButton;

        SlotViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.text_slot_title);
            statusText = itemView.findViewById(R.id.text_slot_status);
            layoutSlotContent = itemView.findViewById(R.id.layout_slot_content);
            registerButton = itemView.findViewById(R.id.btn_register_slot);
            waitingListButton = itemView.findViewById(R.id.btn_waiting_list);
            cancelWaitingListButton = itemView.findViewById(R.id.btn_cancel_waiting_list);
        }

        void bind(final ApiService.SlotCard slot) {
            Context context = itemView.getContext();
            String titleText = context.getString(R.string.presenter_home_slot_title_format,
                    safe(slot.dayOfWeek), safe(slot.date));
            title.setText(titleText);
            
            // Calculate slot capacity info early (needed for onClick handler)
            // NOTE: Backend now properly calculates capacity including pending registrations
            // Use enrolledCount as fallback if approvedCount is not available
            int approved = (slot.enrolledCount > slot.approvedCount) ? slot.enrolledCount : slot.approvedCount;
            int pending = slot.pendingCount;
            int totalOccupied = approved + pending;
            boolean isFull = (totalOccupied >= slot.capacity);
            
            // Log capacity calculation for debugging
            if (slot.slotId != null && totalOccupied > 0) {
                String capacityDebug = String.format("Slot %d capacity: approved=%d, pending=%d, total=%d, capacity=%d, isFull=%s",
                    slot.slotId, approved, pending, totalOccupied, slot.capacity, isFull);
                Logger.i(Logger.TAG_UI, capacityDebug);
                ServerLogger serverLogger = ServerLogger.getInstance(context);
                if (serverLogger != null) {
                    serverLogger.i(ServerLogger.TAG_UI, capacityDebug);
                }
            }
            
            // Set gradient background and status text based on slot state
            String statusTextStr = "";
            int gradientResId = R.drawable.bg_slot_green_gradient; // Default to green (available)
            
            if (isFull) {
                // Red gradient for full slots
                gradientResId = R.drawable.bg_slot_red_gradient;
                statusTextStr = "Full - Join Waiting List";
            } else if (pending > 0) {
                // Yellow gradient for pending slots
                gradientResId = R.drawable.bg_slot_yellow_gradient;
                statusTextStr = context.getString(R.string.presenter_home_slot_pending_line, pending);
            } else {
                // Green gradient for available slots
                gradientResId = R.drawable.bg_slot_green_gradient;
                statusTextStr = context.getString(R.string.presenter_home_slot_state_available);
            }
            
            // Apply gradient background
            if (layoutSlotContent != null) {
                layoutSlotContent.setBackground(ContextCompat.getDrawable(context, gradientResId));
            }
            
            // Set status text
            if (statusText != null) {
                statusText.setText(statusTextStr);
                statusText.setVisibility(View.VISIBLE);
            }
            
            // Make card clickable - clicking card triggers register action
            // Allow registration attempt even if canRegister is false (server will validate)
            itemView.setOnClickListener(v -> {
                // Log slot click action
                Logger.userAction("Slot Clicked", "User clicked slot=" + (slot != null ? slot.slotId : "null") + 
                    ", isFull=" + isFull);
                
                // Check if user is registered in this slot
                boolean isUserRegisteredInThisSlot = slot.alreadyRegistered || 
                        "APPROVED".equals(slot.approvalStatus) || 
                        "PENDING_APPROVAL".equals(slot.approvalStatus);
                
                // If user is already registered in this slot, don't show registration dialog
                if (isUserRegisteredInThisSlot) {
                    Logger.userAction("Slot Clicked - Already Registered", "User clicked slot=" + (slot != null ? slot.slotId : "null") + 
                        " but is already registered. Not showing registration dialog.");
                    if (listener != null) {
                        listener.onSlotClicked(slot, isFull);
                    }
                    return;
                }
                
                // If slot is full and user is not registered, offer waiting list via toast
                if (isFull && !slot.onWaitingList) {
                    Logger.userAction("Full Slot Clicked", "User clicked full slot=" + (slot != null ? slot.slotId : "null") + 
                        ", showing waiting list offer toast");
                    android.widget.Toast.makeText(context, 
                        context.getString(R.string.presenter_slot_full_offer_waiting_list),
                        android.widget.Toast.LENGTH_LONG).show();
                    // Notify listener for server logging
                    if (listener != null) {
                        listener.onSlotClicked(slot, true);
                    }
                } else if (!isFull) {
                    // Allow registration attempt if slot is not full (using consistent isFull check) and user is not registered
                    if (listener != null) {
                        listener.onSlotClicked(slot, false);
                        listener.onRegisterClicked(slot);
                    }
                } else if (listener != null) {
                    // Log other slot clicks (e.g., user is on waiting list but slot is full)
                    listener.onSlotClicked(slot, isFull);
                }
            });

            // Show/hide register button (isFull already declared above)
            if (!slot.canRegister || slot.alreadyRegistered || isFull) {
                registerButton.setVisibility(View.GONE);
            } else {
                registerButton.setVisibility(View.VISIBLE);
                registerButton.setOnClickListener(v -> {
                    Logger.userAction("Register Slot", "Attempting to register for slot=" + slot.slotId);
                    if (listener != null) {
                        listener.onRegisterClicked(slot);
                    }
                });
            }
            
            // Show/hide waiting list button
            // Only show if: slot is full, user is NOT registered in this slot, user is NOT already on waiting list, and waiting list is NOT full
            // Check if user is registered/approved/pending in THIS specific slot
            boolean isUserRegisteredInThisSlot = slot.alreadyRegistered || 
                    "APPROVED".equals(slot.approvalStatus) || 
                    "PENDING_APPROVAL".equals(slot.approvalStatus);
            
            // Waiting list capacity is 1 (max 1 person can be on waiting list)
            int wlCount = slot.waitingListCount > 0 ? slot.waitingListCount : (slot.onWaitingList ? 1 : 0);
            boolean isWaitingListFull = wlCount >= 1;
            
            // Log waiting list status for debugging
            if (slot.onWaitingList) {
                Logger.i(Logger.TAG_UI, "Slot " + slot.slotId + " - User is on waiting list: onWaitingList=" + 
                    slot.onWaitingList + ", waitingListCount=" + slot.waitingListCount);
            }
            
            // Also check if user's name appears in the registered presenters list for this slot
            // Note: slot.alreadyRegistered and slot.approvalStatus already indicate if user is registered
            // The registered list contains PresenterCoPresenter which doesn't have username field
            // So we rely on alreadyRegistered and approvalStatus for this check
            
            // IMPORTANT: Don't show "Join Waiting List" if:
            // 1. User is already on waiting list
            // 2. User is already registered in this slot
            // 3. Slot is not full
            // 4. Waiting list is full (capacity is 1)
            if (isFull && !isUserRegisteredInThisSlot && !slot.onWaitingList && !isWaitingListFull) {
                waitingListButton.setVisibility(View.VISIBLE);
                waitingListButton.setOnClickListener(v -> {
                    Logger.userAction("Join Waiting List", "Attempting to join waiting list for slot=" + slot.slotId);
                    if (listener != null) {
                        listener.onJoinWaitingList(slot);
                    }
                });
            } else {
                waitingListButton.setVisibility(View.GONE);
                // Log why button is hidden
                if (slot.onWaitingList) {
                    Logger.i(Logger.TAG_UI, "Join Waiting List button hidden - user already on waiting list for slot=" + slot.slotId);
                } else if (isUserRegisteredInThisSlot) {
                    Logger.i(Logger.TAG_UI, "Join Waiting List button hidden - user already registered in slot=" + slot.slotId);
                } else if (!isFull) {
                    Logger.i(Logger.TAG_UI, "Join Waiting List button hidden - slot is not full, slot=" + slot.slotId);
                } else if (isWaitingListFull) {
                    Logger.i(Logger.TAG_UI, "Join Waiting List button hidden - waiting list is full (1/1), slot=" + slot.slotId);
                }
            }
            
            // Show/hide cancel waiting list button
            if (slot.onWaitingList) {
                cancelWaitingListButton.setVisibility(View.VISIBLE);
                cancelWaitingListButton.setOnClickListener(v -> {
                    Logger.userAction("Cancel Waiting List", "Attempting to cancel waiting list for slot=" + slot.slotId);
                    if (listener != null) {
                        listener.onCancelWaitingList(slot);
                    }
                });
            } else {
                cancelWaitingListButton.setVisibility(View.GONE);
            }

        }

        private String safe(String value) {
            return value == null ? "" : value;
        }
    }
}
