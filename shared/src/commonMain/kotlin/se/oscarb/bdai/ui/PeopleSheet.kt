package se.oscarb.bdai.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import bdai.shared.generated.resources.Res
import bdai.shared.generated.resources.cd_delete_person
import bdai.shared.generated.resources.people_add_person
import bdai.shared.generated.resources.people_empty_state
import bdai.shared.generated.resources.people_filter_families
import bdai.shared.generated.resources.people_filter_upcoming
import bdai.shared.generated.resources.people_sheet_subtitle
import bdai.shared.generated.resources.people_sheet_title
import bdai.shared.generated.resources.people_sync_nas
import bdai.shared.generated.resources.person_age_turning
import bdai.shared.generated.resources.person_children_label
import bdai.shared.generated.resources.person_days_today
import bdai.shared.generated.resources.person_days_tomorrow
import bdai.shared.generated.resources.person_days_until
import bdai.shared.generated.resources.person_form_add_title
import bdai.shared.generated.resources.person_form_birthday
import bdai.shared.generated.resources.person_form_cancel
import bdai.shared.generated.resources.person_form_friend_of
import bdai.shared.generated.resources.person_form_name
import bdai.shared.generated.resources.person_form_partner_birthday
import bdai.shared.generated.resources.person_form_partner_name
import bdai.shared.generated.resources.person_form_save
import bdai.shared.generated.resources.person_friend_of
import bdai.shared.generated.resources.person_no_birthday
import bdai.shared.generated.resources.person_parents_label
import bdai.shared.generated.resources.person_partner_label
import org.jetbrains.compose.resources.stringResource
import se.oscarb.bdai.core.designsystem.AppIcons
import se.oscarb.bdai.domain.FlatBirthdayPerson
import se.oscarb.bdai.domain.PersonEntry

private enum class PeopleFilterTab {
    UPCOMING,
    FAMILIES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeopleSheet(
    state: BdaiChatState,
    onAction: (BdaiAction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableStateOf(PeopleFilterTab.UPCOMING) }
    var editingPerson by remember { mutableStateOf<PersonEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { onAction(BdaiAction.OnDismissPeopleSheet) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = AppIcons.Cake,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.people_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(Res.string.people_sheet_subtitle, state.flatBirthdays.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { onAction(BdaiAction.OnSyncWithNasClick) },
                    enabled = !state.isSyncing,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painter = AppIcons.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = stringResource(Res.string.people_sync_nas),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                FilledTonalIconButton(
                    onClick = {
                        editingPerson = null
                        showAddDialog = true
                    }
                ) {
                    Icon(
                        painter = AppIcons.Plus,
                        contentDescription = stringResource(Res.string.people_add_person)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == PeopleFilterTab.UPCOMING,
                    onClick = { selectedTab = PeopleFilterTab.UPCOMING },
                    label = { Text(stringResource(Res.string.people_filter_upcoming)) }
                )
                FilterChip(
                    selected = selectedTab == PeopleFilterTab.FAMILIES,
                    onClick = { selectedTab = PeopleFilterTab.FAMILIES },
                    label = { Text(stringResource(Res.string.people_filter_families)) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            when (selectedTab) {
                PeopleFilterTab.UPCOMING -> {
                    if (state.flatBirthdays.isEmpty()) {
                        EmptyPeopleState(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = state.flatBirthdays,
                                key = { it.key }
                            ) { person ->
                                UpcomingBirthdayCard(
                                    person = person,
                                    onDeleteClick = {
                                        onAction(
                                            BdaiAction.OnDeletePerson(
                                                name = person.name,
                                                birthdayHint = person.birthday.toString(),
                                                parentNameHint = person.parentNames.firstOrNull()
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                PeopleFilterTab.FAMILIES -> {
                    if (state.entries.isEmpty()) {
                        EmptyPeopleState(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = state.entries,
                                key = { "${it.name}:${it.birthday.orEmpty()}" }
                            ) { entry ->
                                FamilyEntryCard(
                                    entry = entry,
                                    onEditClick = {
                                        editingPerson = entry
                                        showAddDialog = true
                                    },
                                    onDeleteClick = {
                                        onAction(
                                            BdaiAction.OnDeletePerson(
                                                name = entry.name,
                                                birthdayHint = entry.birthday
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddOrEditPersonDialog(
            initial = editingPerson,
            onDismiss = { showAddDialog = false },
            onSave = { name, birthday, friendOf, partnerName, partnerBirthday ->
                onAction(
                    BdaiAction.OnUpsertPerson(
                        name = name,
                        birthday = birthday,
                        friendOf = friendOf,
                        partnerName = partnerName,
                        partnerBirthday = partnerBirthday
                    )
                )
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun UpcomingBirthdayCard(
    person: FlatBirthdayPerson,
    onDeleteClick: () -> Unit
) {
    val isSoon = person.daysUntilNextBirthday in 0..7
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSoon) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    person.friendOf?.let { friend ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = stringResource(Res.string.person_friend_of, friend),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(
                        Res.string.person_age_turning,
                        person.ageNow,
                        person.turningAge,
                        person.birthday.toString()
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (person.parentNames.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            Res.string.person_parents_label,
                            person.parentNames.joinToString(" & ")
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                person.partnerName?.let { partner ->
                    Text(
                        text = stringResource(Res.string.person_partner_label, partner),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (person.childNames.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            Res.string.person_children_label,
                            person.childNames.joinToString(", ")
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSoon) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (isSoon) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            ) {
                val badgeText = when (person.daysUntilNextBirthday) {
                    0 -> stringResource(Res.string.person_days_today)
                    1 -> stringResource(Res.string.person_days_tomorrow)
                    else -> stringResource(Res.string.person_days_until, person.daysUntilNextBirthday)
                }
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = AppIcons.Trash,
                    contentDescription = stringResource(Res.string.cd_delete_person),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FamilyEntryCard(
    entry: PersonEntry,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    entry.friendOf?.let { friend ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = stringResource(Res.string.person_friend_of, friend),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = AppIcons.Trash,
                        contentDescription = stringResource(Res.string.cd_delete_person),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = entry.birthday ?: stringResource(Res.string.person_no_birthday),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            entry.partner?.let { partner ->
                Text(
                    text = stringResource(
                        Res.string.person_partner_label,
                        if (partner.birthday != null) "${partner.name} (${partner.birthday})" else partner.name
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (entry.children.isNotEmpty()) {
                Text(
                    text = stringResource(
                        Res.string.person_children_label,
                        entry.children.joinToString(", ") { "${it.name} (${it.birthday})" }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyPeopleState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(Res.string.people_empty_state),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AddOrEditPersonDialog(
    initial: PersonEntry?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        birthday: String?,
        friendOf: String?,
        partnerName: String?,
        partnerBirthday: String?
    ) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var birthday by remember(initial) { mutableStateOf(initial?.birthday.orEmpty()) }
    var friendOf by remember(initial) { mutableStateOf(initial?.friendOf.orEmpty()) }
    var partnerName by remember(initial) { mutableStateOf(initial?.partner?.name.orEmpty()) }
    var partnerBirthday by remember(initial) { mutableStateOf(initial?.partner?.birthday.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.person_form_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.person_form_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = birthday,
                    onValueChange = { birthday = it },
                    label = { Text(stringResource(Res.string.person_form_birthday)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = friendOf,
                    onValueChange = { friendOf = it },
                    label = { Text(stringResource(Res.string.person_form_friend_of)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = partnerName,
                    onValueChange = { partnerName = it },
                    label = { Text(stringResource(Res.string.person_form_partner_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = partnerBirthday,
                    onValueChange = { partnerBirthday = it },
                    label = { Text(stringResource(Res.string.person_form_partner_birthday)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            name,
                            birthday.takeIf { it.isNotBlank() },
                            friendOf.takeIf { it.isNotBlank() },
                            partnerName.takeIf { it.isNotBlank() },
                            partnerBirthday.takeIf { it.isNotBlank() }
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(Res.string.person_form_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.person_form_cancel))
            }
        }
    )
}
