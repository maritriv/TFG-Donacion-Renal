package com.lhc.tfg_prediccion.ui.control

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lhc.tfg_prediccion.R

class UserListAdapter(
    private val onStatusClick: (UserItem) -> Unit,
    private val onItemClick: (UserItem) -> Unit
): ListAdapter<UserItem, UserListAdapter.UserViewHolder>(DiffCallback()) {

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name = itemView.findViewById<TextView>(R.id.tvUserName)
        private val email = itemView.findViewById<TextView>(R.id.tvUserEmail)
        private val badge = itemView.findViewById<TextView>(R.id.tvStatus)

        fun bind(item: UserItem) {
            name.text = item.fullName
            email.text = item.email

            // Cambiar color y texto del estado visualmente
            if (item.isActive) {
                badge.text = "Activo"
                badge.setBackgroundResource(R.drawable.bg_status_active)
            } else {
                badge.text = "Inactivo"
                badge.setBackgroundResource(R.drawable.bg_status_inactive)
            }

            // AL PULSAR -> llamar al callback
            badge.setOnClickListener { onStatusClick(item) }

            // Pulsar el item -> ver perfil
            itemView.setOnClickListener { onItemClick(item) }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<UserItem>() {
        override fun areItemsTheSame(oldItem: UserItem, newItem: UserItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: UserItem, newItem: UserItem) =
            oldItem == newItem
    }
}
