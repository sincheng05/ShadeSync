@file:Suppress("DEPRECATION")

package com.example.shadesync.cloud

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.Sheet
import com.google.api.services.sheets.v4.model.SheetProperties
import com.google.api.services.sheets.v4.model.Spreadsheet
import com.google.api.services.sheets.v4.model.SpreadsheetProperties
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class GoogleUploadResult(
    val driveFileId: String,
    val driveWebLink: String,
    val spreadsheetId: String
)

internal class GoogleCloudManager(
    context: Context,
    account: GoogleSignInAccount
) {
    private val credential = GoogleAccountCredential.usingOAuth2(
        context.applicationContext,
        listOf(DriveScopes.DRIVE_FILE, SheetsScopes.SPREADSHEETS)
    ).apply {
        selectedAccount = requireNotNull(account.account) {
            "Google sign-in did not return an Android account handle."
        }
    }

    private val driveService = Drive.Builder(
        AndroidHttp.newCompatibleTransport(),
        GsonFactory.getDefaultInstance(),
        credential
    ).setApplicationName(APP_NAME).build()

    private val sheetsService = Sheets.Builder(
        AndroidHttp.newCompatibleTransport(),
        GsonFactory.getDefaultInstance(),
        credential
    ).setApplicationName(APP_NAME).build()

    suspend fun uploadMeasurement(
        record: MeasurementUploadRecord,
        imageBytes: ByteArray
    ): GoogleUploadResult = withContext(Dispatchers.IO) {
        val folderId = ensureShadeSyncFolder()
        val spreadsheetId = ensureMeasurementsSpreadsheet(folderId)
        ensureSpreadsheetHeaders(spreadsheetId)

        val imageFile = uploadImageFile(
            folderId = folderId,
            fileName = "ShadeSync_Tooth_${record.capturedAtUnixMs}.jpg",
            imageBytes = imageBytes
        )
        val imageFileId = requireNotNull(imageFile.id) { "Drive upload did not return a file id." }
        val driveWebLink = imageFile.webViewLink ?: defaultDriveWebLink(imageFileId)

        val values = ValueRange().setValues(
            listOf(record.toSheetRow(imageFileId = imageFileId, imageWebLink = driveWebLink))
        )
        sheetsService.spreadsheets().values()
            .append(spreadsheetId, "$WORKSHEET_NAME!A:A", values)
            .setValueInputOption("USER_ENTERED")
            .execute()

        GoogleUploadResult(
            driveFileId = imageFileId,
            driveWebLink = driveWebLink,
            spreadsheetId = spreadsheetId
        )
    }

    private fun ensureShadeSyncFolder(): String {
        findDriveFileId(
            name = ROOT_FOLDER_NAME,
            mimeType = DRIVE_FOLDER_MIME
        )?.let { return it }

        val folderMetadata = DriveFile().apply {
            name = ROOT_FOLDER_NAME
            mimeType = DRIVE_FOLDER_MIME
        }
        return requireNotNull(
            driveService.files().create(folderMetadata)
                .setFields("id")
                .execute()
                .id
        ) { "Drive folder creation did not return an id." }
    }

    private fun ensureMeasurementsSpreadsheet(folderId: String): String {
        findDriveFileId(
            name = SPREADSHEET_NAME,
            mimeType = DRIVE_SPREADSHEET_MIME,
            parentId = folderId
        )?.let { return it }

        val spreadsheet = Spreadsheet().setProperties(
            SpreadsheetProperties().setTitle(SPREADSHEET_NAME)
        ).setSheets(
            listOf(
                Sheet().setProperties(
                    SheetProperties().setTitle(WORKSHEET_NAME)
                )
            )
        )

        val createdSpreadsheetId = requireNotNull(
            sheetsService.spreadsheets()
                .create(spreadsheet)
                .setFields("spreadsheetId")
                .execute()
                .spreadsheetId
        ) { "Sheets creation did not return a spreadsheet id." }

        moveDriveFileIntoFolder(fileId = createdSpreadsheetId, folderId = folderId)
        return createdSpreadsheetId
    }

    private fun ensureSpreadsheetHeaders(spreadsheetId: String) {
        val firstCell = sheetsService.spreadsheets().values()
            .get(spreadsheetId, "$WORKSHEET_NAME!A1:A1")
            .execute()
            .getValues()

        if (firstCell.isNullOrEmpty()) {
            sheetsService.spreadsheets().values()
                .update(
                    spreadsheetId,
                    "$WORKSHEET_NAME!A1",
                    ValueRange().setValues(listOf(MeasurementUploadRecord.headers))
                )
                .setValueInputOption("RAW")
                .execute()
        }
    }

    private fun uploadImageFile(
        folderId: String,
        fileName: String,
        imageBytes: ByteArray
    ): DriveFile {
        val metadata = DriveFile().apply {
            name = fileName
            mimeType = JPEG_MIME
            parents = listOf(folderId)
        }

        return driveService.files()
            .create(metadata, ByteArrayContent(JPEG_MIME, imageBytes))
            .setFields("id, webViewLink")
            .execute()
    }

    private fun moveDriveFileIntoFolder(
        fileId: String,
        folderId: String
    ) {
        val existingParents = driveService.files()
            .get(fileId)
            .setFields("parents")
            .execute()
            .parents
            .orEmpty()
            .joinToString(",")

        val updateRequest = driveService.files().update(fileId, null)
            .setAddParents(folderId)
            .setFields("id, parents")

        if (existingParents.isNotBlank()) {
            updateRequest.setRemoveParents(existingParents)
        }

        updateRequest.execute()
    }

    private fun findDriveFileId(
        name: String,
        mimeType: String,
        parentId: String? = null
    ): String? {
        val parentClause = parentId?.let { " and '$it' in parents" }.orEmpty()
        val query = "name = '${name.escapeDriveQuery()}' and mimeType = '$mimeType' and trashed = false$parentClause"
        val files = driveService.files()
            .list()
            .setSpaces("drive")
            .setQ(query)
            .setFields("files(id, name)")
            .setPageSize(1)
            .execute()
            .files
            .orEmpty()

        return files.firstOrNull()?.id
    }

    private fun defaultDriveWebLink(fileId: String): String {
        return "https://drive.google.com/file/d/$fileId/view"
    }

    private fun String.escapeDriveQuery(): String {
        return replace("'", "\\'")
    }

    private companion object {
        const val APP_NAME = "ShadeSync"
        const val ROOT_FOLDER_NAME = "ShadeSync"
        const val SPREADSHEET_NAME = "ShadeSync Records"
        const val WORKSHEET_NAME = "Measurements"
        const val DRIVE_FOLDER_MIME = "application/vnd.google-apps.folder"
        const val DRIVE_SPREADSHEET_MIME = "application/vnd.google-apps.spreadsheet"
        const val JPEG_MIME = "image/jpeg"
    }
}
