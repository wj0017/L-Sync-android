package com.lsync.app.data.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// 파일 공유 공용 유틸(리포트 전용 아님). Hilt 등록 대상이 아닌 top-level 함수다.
//
// 이 프로젝트의 기존 내보내기(FinanceScreen CSV)는 EXTRA_TEXT만 쓰므로 파일 URI 공유 선례가 없다.
// 아래 세 가지는 빠뜨리면 런타임에만 드러나는 함정이라 여기에 모아 둔다.

private const val TAG = "ShareFiles"

// res/xml/file_paths.xml의 <cache-path path="shared/" />와 문자열까지 일치해야 한다.
// 어긋나면 IllegalArgumentException: Failed to find configured root.
private const val SHARED_DIR = "shared"

// AndroidManifest의 android:authorities="${applicationId}.fileprovider"와 일치.
// applicationId는 빌드 타입별로 달라질 수 있으므로 하드코딩하지 않고 packageName에서 만든다.
private const val AUTHORITY_SUFFIX = ".fileprovider"

/**
 * `cacheDir/shared` 아래에 PNG를 쓰고 FileProvider content URI를 돌려준다.
 * 실패하면 예외를 삼키고 null을 반환한다(호출자가 대체 경로를 안내한다).
 */
suspend fun savePngForShare(context: Context, bitmap: Bitmap, fileName: String): Uri? =
    withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, SHARED_DIR)
            // 이전 공유 파일 정리는 "다음 저장 직전"에만 한다.
            // 공유 직후 삭제하면 수신 앱(카카오톡·Gmail 등)이 비동기로 읽기 전에 파일이 사라진다.
            if (dir.exists()) dir.listFiles()?.forEach { it.delete() }
            dir.mkdirs()

            val file = File(dir, fileName)
            val ok = FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (!ok) {
                Log.w(TAG, "PNG 압축 실패 ($fileName)")
                return@withContext null
            }
            FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
        }.getOrElse { e ->
            Log.w(TAG, "공유용 PNG 저장 실패 ($fileName)", e)
            null
        }
    }

/**
 * 이미지 공유 Intent.
 * - `FLAG_GRANT_READ_URI_PERMISSION`이 없으면 수신 앱에서 SecurityException이 난다.
 * - `clipData`가 없으면 Android 13+ Sharesheet에 썸네일 미리보기가 뜨지 않는다.
 */
fun buildImageShareIntent(context: Context, uri: Uri, subject: String): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, subject, uri)
    }

/** 텍스트 공유 Intent (FinanceScreen CSV 내보내기와 같은 형태). */
fun buildTextShareIntent(text: String, subject: String): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
