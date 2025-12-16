package org.example.semscan.ui.teacher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
        private final TextView schedule;
        private final TextView location;
        private final TextView approvedCount;
        private final TextView pendingCount;
        private final TextView waitingListCount;
        private final TextView presenters;
        private final TextView badge;
        private final MaterialButton registerButton;
        private final MaterialButton waitingListButton;
        private final MaterialButton cancelWaitingListButton;

        SlotViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.text_slot_title);
            schedule = itemView.findViewById(R.id.text_slot_schedule);
            location = itemView.findViewById(R.id.text_slot_location);
            approvedCount = itemView.findViewById(R.id.text_slot_approved);
            pendingCount = itemView.findViewById(R.id.text_slot_pending);
            waitingListCount = itemView.findViewById(R.id.text_slot_waiting_list);
            presenters = itemView.findViewById(R.id.text_slot_presenters);
            badge = itemView.findViewById(R.id.text_slot_badge);
            registerButton = itemView.findViewById(R.id.btn_register_slot);
            waitingListButton = itemView.findViewById(R.id.btn_waiting_list);
            cancelWaitingListButton = itemView.findViewById(R.id.btn_cancel_waiting_list);
        }

        void bind(final ApiService.SlotCard slot) {
            Context context = itemView.getContext();
            String titleText = context.getString(R.string.presenter_home_slot_title_format,
                    safe(slot.dayOfWeek), safe(slot.date));
            title.setText(titleText);

            schedule.setText(safe(slot.timeRange));
            
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
                
                // If slot is full and user is not registered, offer waiting list via toast
                if (isFull && !isUserRegisteredInThisSlot && !slot.onWaitingList) {
                    Logger.userAction("Full Slot Clicked", "User clicked full slot=" + (slot != null ? slot.slotId : "null") + 
                        ", showing waiting list offer toast");
                    android.widget.Toast.makeText(context, 
                        context.getString(R.string.presenter_slot_full_offer_waiting_list),
                        android.widget.Toast.LENGTH_LONG).show();
                    // Notify listener for server logging
                    if (listener != null) {
                        listener.onSlotClicked(slot, true);
                    }
                } else if (!slot.alreadyRegistered && slot.state != ApiService.SlotState.FULL) {
                    // Allow registration attempt if slot is not full
                    if (listener != null) {
                        listener.onSlotClicked(slot, false);
                        listener.onRegisterClicked(slot);
                    }
                } else if (listener != null) {
                    // Log other slot clicks
                    listener.onSlotClicked(slot, isFull);
                }
            });

            StringBuilder venue = new StringBuilder();
            if (slot.room != null && !slot.room.isEmpty()) {
                venue.append(context.getString(R.string.room_with_label, slot.room));
            }
            if (slot.building != null && !slot.building.isEmpty()) {
                if (venue.length() > 0) {
                    venue.append(" • ");
                }
                venue.append(context.getString(R.string.building_with_label, slot.building));
            }
            location.setText(venue.toString());
            location.setVisibility(venue.length() > 0 ? View.VISIBLE : View.GONE);

            // approved, pending, and isFull are already calculated above for onClick handler
            
            // Show approved count line (if > 0)
            if (approved > 0) {
                // Try to show names from registered list (assuming first 'approved' entries are approved)
                String approvedNames = formatNamesForDisplay(slot.registered, approved);
                if (approvedNames != null && !approvedNames.trim().isEmpty()) {
                    approvedCount.setText(context.getString(R.string.presenter_home_slot_approved_line_with_names,
                        approved, slot.capacity, approvedNames));
                } else {
                    approvedCount.setText(context.getString(R.string.presenter_home_slot_approved_line,
                        approved, slot.capacity));
                }
                approvedCount.setVisibility(View.VISIBLE);
            } else {
                approvedCount.setVisibility(View.GONE);
            }
            
            // Show pending count line (if > 0)
            // Note: Backend doesn't provide separate pending names list, so we show count only
            if (pending > 0) {
                // TODO: If backend provides pendingNames list, show names here
                pendingCount.setText(context.getString(R.string.presenter_home_slot_pending_line, pending));
                pendingCount.setVisibility(View.VISIBLE);
            } else {
                pendingCount.setVisibility(View.GONE);
            }
            
            // Show waiting list line if there are people on the waiting list
            // Show to everyone, not just the person on it
            int wlCount = slot.waitingListCount > 0 ? slot.waitingListCount : (slot.onWaitingList ? 1 : 0);
            
            // Log for debugging (both local and server)
            if (slot.slotId != null) {
                String wlDebugMsg = "Slot " + slot.slotId + " - onWaitingList=" + slot.onWaitingList + 
                    ", waitingListCount=" + slot.waitingListCount + ", calculated wlCount=" + wlCount;
                Logger.i(Logger.TAG_UI, wlDebugMsg);
                // Also log to server
                ServerLogger serverLogger = ServerLogger.getInstance(context);
                if (serverLogger != null) {
                    serverLogger.i(ServerLogger.TAG_UI, wlDebugMsg);
                }
            }
            
            if (wlCount > 0) {
                // Only show name if current user is on the waiting list (for privacy)
                String waitingListText;
                if (slot.onWaitingList) {
                    // Current user is on waiting list - show their FULL NAME (not username)
                    // Always prefer full name from PreferencesManager over backend's waitingListUserName
                    // (backend might return username like "talguest3" instead of full name)
                    PreferencesManager prefs = PreferencesManager.getInstance(context);
                    String firstName = prefs.getFirstName();
                    String lastName = prefs.getLastName();
                    String userName = null;
                    
                    // Build full name from PreferencesManager
                    if (firstName != null && !firstName.trim().isEmpty()) {
                        userName = firstName.trim();
                        if (lastName != null && !lastName.trim().isEmpty()) {
                            userName += " " + lastName.trim();
                        }
                    }
                    
                    // Fallback to backend's waitingListUserName only if PreferencesManager doesn't have name
                    if ((userName == null || userName.trim().isEmpty()) && 
                        slot.waitingListUserName != null && !slot.waitingListUserName.trim().isEmpty()) {
                        userName = slot.waitingListUserName.trim();
                    }
                    
                    // Format: "waiting list 1/1 - John Doe" (with name for current user)
                    if (userName != null && !userName.trim().isEmpty()) {
                        waitingListText = context.getString(R.string.presenter_home_slot_waiting_list_line_with_name,
                            wlCount, wlCount, userName);
                    } else {
                        waitingListText = context.getString(R.string.presenter_home_slot_waiting_list_line_with_count,
                            wlCount, wlCount);
                    }
                } else {
                    // Current user is NOT on waiting list - show count only (no name for privacy)
                    waitingListText = context.getString(R.string.presenter_home_slot_waiting_list_line_with_count,
                        wlCount, wlCount);
                }
                waitingListCount.setText(waitingListText);
                waitingListCount.setVisibility(View.VISIBLE);
            } else {
                waitingListCount.setVisibility(View.GONE);
            }

            String presentersText = formatPresenters(slot.registered);
            if (presentersText != null) {
                presenters.setText(presentersText);
                presenters.setVisibility(View.VISIBLE);
            } else {
                presenters.setVisibility(View.GONE);
            }

            // Update badge logic to show approval status (approved, pending, totalOccupied, and isFull already declared above)
            badge.setVisibility(View.GONE);
            // totalOccupied already calculated above
            
            // Priority order for badge display:
            // 1. Pending approvals (yellow)
            // 2. User's own registration status
            // 3. Slot capacity status (full/partial/available)
            
            if (pending > 0) {
                // Show pending count badge
                badge.setText(context.getString(R.string.presenter_slot_pending_count, pending));
                badge.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_slot_status_yellow));
                badge.setVisibility(View.VISIBLE);
            } else if (slot.alreadyRegistered) {
                // User's own registration
                if ("PENDING_APPROVAL".equals(slot.approvalStatus)) {
                    badge.setText(R.string.presenter_slot_pending_approval);
                    badge.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_slot_status_yellow));
                } else {
                    badge.setText(R.string.presenter_slot_registered_badge);
                    badge.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_slot_status));
                }
                badge.setVisibility(View.VISIBLE);
            } else if (isFull) {
                // Slot is full
                badge.setText(R.string.presenter_slot_full_badge);
                badge.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_slot_status_red));
                badge.setVisibility(View.VISIBLE);
            } else if (totalOccupied > 0 && totalOccupied < slot.capacity) {
                // Slot is partially booked (has some registrations but not full)
                badge.setText(R.string.presenter_home_slot_state_partial);
                badge.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_slot_status_yellow));
                badge.setVisibility(View.VISIBLE);
            } else if (totalOccupied == 0) {
                // Slot is completely available
                badge.setText(R.string.presenter_home_slot_state_available);
                badge.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_slot_status_green));
                badge.setVisibility(View.VISIBLE);
            } else {
                // Fallback to slot.state if counts are not available
                if (slot.state == ApiService.SlotState.SEMI) {
                    badge.setText(R.string.presenter_home_slot_state_partial);
                    badge.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_slot_status_yellow));
                    badge.setVisibility(View.VISIBLE);
                } else if (slot.state == ApiService.SlotState.FREE) {
                    badge.setText(R.string.presenter_home_slot_state_available);
                    badge.setBackground(ContextCompat.getDrawable(context, R.drawable.bg_slot_status_green));
                    badge.setVisibility(View.VISIBLE);
                }
            }

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
            // Reuse wlCount that was calculated earlier for the waiting list display
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

            // Show disable reason or default message if can't register
            // But don't show "registered in another slot" if user is registered in THIS slot
            if (!slot.canRegister) {
                // Check if user is registered in THIS slot
                boolean isUserInThisSlot = slot.alreadyRegistered || 
                        "APPROVED".equals(slot.approvalStatus) || 
                        "PENDING_APPROVAL".equals(slot.approvalStatus);
                
                // Note: slot.alreadyRegistered and slot.approvalStatus already indicate if user is registered
                // The registered list contains PresenterCoPresenter which doesn't have username field
                // So we rely on alreadyRegistered and approvalStatus for this check
                
                // Only show disable reason if user is NOT registered in this slot
                if (!isUserInThisSlot) {
                    if (slot.disableReason != null && slot.disableReason.trim().length() > 0) {
                        location.setText(slot.disableReason);
                    } else {
                        // Default message when can't register but no specific reason provided
                        location.setText(context.getString(R.string.presenter_slot_registered_in_another));
                    }
                    location.setVisibility(View.VISIBLE);
                } else {
                    // User is registered in this slot - don't show disable message
                    location.setVisibility(View.GONE);
                }
            }
        }

        private String safe(String value) {
            return value == null ? "" : value;
        }
    }
}
