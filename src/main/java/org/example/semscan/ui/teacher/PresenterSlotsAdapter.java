package org.example.semscan.ui.teacher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.Spanned;
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
import org.example.semscan.utils.ConfigManager;
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
    private boolean userHasApprovedRegistration = false;

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
        userHasApprovedRegistration = false;
        if (slots != null) {
            items.addAll(slots);
            for (ApiService.SlotCard slot : slots) {
                if ("APPROVED".equals(slot.approvalStatus)) {
                    userHasApprovedRegistration = true;
                    break;
                }
            }
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
     * Format names from registered list for display - each name on its own indented line
     * Shows degree prefix (PhD/MSc) to explain capacity usage
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
                // Format: "    PhD, Name" or "    MSc, Name" or just "    Name" if no degree
                StringBuilder entry = new StringBuilder("    ");
                if (presenter.degree != null && !presenter.degree.trim().isEmpty()) {
                    entry.append(presenter.degree.trim()).append(", ");
                }
                entry.append(presenter.name.trim());
                names.add(entry.toString());
            }
        }
        if (names.isEmpty()) {
            return null;
        }
        // Each name on its own line
        return TextUtils.join("\n", names);
    }

    /**
     * Format waiting list as priority numbers with names (#1 - Name)
     */
    private String formatWaitingListPriorities(List<ApiService.PresenterCoPresenter> waitingList, int maxCount) {
        if (waitingList == null || waitingList.isEmpty()) {
            return null;
        }
        List<String> priorities = new ArrayList<>();
        int count = Math.min(maxCount, waitingList.size());
        for (int i = 0; i < count; i++) {
            ApiService.PresenterCoPresenter presenter = waitingList.get(i);
            String name = (presenter != null && presenter.name != null) ? presenter.name.trim() : "";
            priorities.add("    #" + (i + 1) + " - " + name);
        }
        return TextUtils.join("\n", priorities);
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
            // Format date as dd/mm/yyyy
            String formattedDate = safe(slot.date);
            if (formattedDate.contains("-")) {
                String[] parts = formattedDate.split("-");
                if (parts.length == 3) {
                    formattedDate = parts[2] + "/" + parts[1] + "/" + parts[0];
                }
            }
            String titleText = context.getString(R.string.presenter_home_slot_title_format,
                    safe(slot.dayOfWeek), formattedDate);
            title.setText(titleText);
            
            // Calculate slot capacity info early (needed for onClick handler)
            // Backend sends EFFECTIVE capacity usage (PhD=2, MSc=1)
            int approved = slot.approvedCount; // Effective capacity used by approved registrations
            int pending = slot.pendingCount;   // Effective capacity used by pending registrations
            
            // For COLOR: Only APPROVED counts as "full" - pending might get declined
            boolean isFull = (approved >= slot.capacity);
            
            // For BUTTON visibility: Both approved AND pending block new registrations
            int totalOccupied = approved + pending;
            boolean slotAtCapacity = (totalOccupied >= slot.capacity);
            
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
            StringBuilder statusBuilder = new StringBuilder();
            int gradientResId = R.drawable.bg_slot_green_gradient; // Default to green (available)
            
            if (isFull) {
                // Red gradient for full slots
                gradientResId = R.drawable.bg_slot_red_gradient;
            } else if (approved > 0 || pending > 0) {
                // Yellow gradient for partially filled slots (has approved or pending but not full)
                gradientResId = R.drawable.bg_slot_yellow_gradient;
            } else {
                // Green gradient for empty/available slots
                gradientResId = R.drawable.bg_slot_green_gradient;
            }
            
            // Build multi-line status text showing approved, pending, and waiting list names
            // Each section has label on its own line, then indented names below
            String approvedNames = formatNamesForDisplay(slot.registered, 5);
            if (approvedNames != null && !approvedNames.isEmpty()) {
                statusBuilder.append("Approved:\n").append(approvedNames);
            }

            // Pending presenter names (if any)
            String pendingNames = formatNamesForDisplay(slot.pendingPresenters, 5);
            if (pendingNames != null && !pendingNames.isEmpty()) {
                if (statusBuilder.length() > 0) {
                    statusBuilder.append("\n\n");
                }
                statusBuilder.append("Pending:\n").append(pendingNames);
            }

            // Waiting list priorities (if any)
            String wlNames = formatWaitingListPriorities(slot.waitingListEntries, 5);
            if (wlNames != null && !wlNames.isEmpty()) {
                if (statusBuilder.length() > 0) {
                    statusBuilder.append("\n\n");
                }
                statusBuilder.append("Waiting List Priorities:\n").append(wlNames);
            }
            
            // Calculate available spots for display
            int availableSpots = slot.capacity - approved;
            if (availableSpots < 0) availableSpots = 0;
            String capacityText = String.format(Locale.getDefault(), "%d/%d", availableSpots, slot.capacity);

            // Build final status text with capacity info
            // Use HTML for bigger availability text
            Spanned statusSpanned;
            if (statusBuilder.length() == 0) {
                if (isFull) {
                    statusSpanned = Html.fromHtml("<big><b>Full (0/" + slot.capacity + ")</b></big> - Join Waiting List", Html.FROM_HTML_MODE_LEGACY);
                } else {
                    String availText = context.getString(R.string.presenter_home_slot_state_available) + " (" + capacityText + ")";
                    statusSpanned = Html.fromHtml("<big><b>" + availText + "</b></big>", Html.FROM_HTML_MODE_LEGACY);
                }
            } else {
                // Prepend capacity info to the names with extra spacing
                String htmlText = "<big><b>" + capacityText + " Available</b></big><br/><br/>" +
                    statusBuilder.toString().replace("\n", "<br/>");
                statusSpanned = Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY);
            }

            // Apply gradient background
            if (layoutSlotContent != null) {
                layoutSlotContent.setBackground(ContextCompat.getDrawable(context, gradientResId));
            }

            // Set status text
            if (statusText != null) {
                statusText.setText(statusSpanned);
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

            // Show/hide register button - use slotAtCapacity for button visibility
            // Also hide if user is on waiting list for this slot
            if (!slot.canRegister || slot.alreadyRegistered || slotAtCapacity || userHasApprovedRegistration || slot.onWaitingList) {
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
            // Get configurable waiting list limit
            int waitingListLimit = ConfigManager.getInstance(context).getWaitingListLimitPerSlot();
            boolean isWaitingListFull = wlCount >= waitingListLimit;
            
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
            // 3. Slot is not full (unless PhD can't fit due to insufficient capacity)
            // 4. Waiting list is full (capacity is 1)
            // 5. User can register normally
            boolean phdCantFit = !slot.canRegister && slot.disableReason != null &&
                    slot.disableReason.contains("waiting list");
            boolean showWaitingList = (slotAtCapacity || phdCantFit) &&
                    !isUserRegisteredInThisSlot && !slot.onWaitingList &&
                    !isWaitingListFull && !userHasApprovedRegistration;
            if (showWaitingList) {
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
