# Load the .NET assembly for the GUI folder picker
Add-Type -AssemblyName System.Windows.Forms

# =====================================================================
# CONFIGURATION
# =====================================================================
$sourceRepoPath = "E:\MISTER_MISCHIEF_APP_V1\app\src\main"
$allowedExtensions = @(".kt", ".kts", ".xml") 
# =====================================================================

# 1. THE GUI FOLDER PICKER
$folderBrowser = New-Object System.Windows.Forms.FolderBrowserDialog
$folderBrowser.Description = "Select the folder where your .txt upload files will be saved."
$folderBrowser.ShowNewFolderButton = $true

if ($folderBrowser.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) {
    Write-Host "No destination folder selected. Sync cancelled. Exiting..." -ForegroundColor Red
    exit
}

$targetTxtPath = $folderBrowser.SelectedPath
Write-Host "====================================================================" -ForegroundColor Cyan
Write-Host " 	TARGET FOLDER LOCKED: $targetTxtPath" -ForegroundColor Cyan
Write-Host "====================================================================" -ForegroundColor Cyan

# 2. THE INITIAL BULK SYNC (The Baseline Sweep)
Write-Host "`n[STARTING] Performing initial baseline sweep of repository..." -ForegroundColor Yellow
$allSourceFiles = Get-ChildItem -Path $sourceRepoPath -Recurse -File | Where-Object {
    $ext = [System.IO.Path]::GetExtension($_.Name).ToLower()
    ($allowedExtensions -contains $ext) -and ($_.FullName -notmatch '\\obj\\' -and $_.FullName -notmatch '\\bin\\')
}

$syncCount = 0
foreach ($file in $allSourceFiles) {
    $dest = Join-Path -Path $targetTxtPath -ChildPath "$($file.Name).txt"
    # Using Native .NET for the sweep too
    [System.IO.File]::Copy($file.FullName, $dest, $true)
    $syncCount++
}
Write-Host "[SUCCESS] Baseline sweep complete. $syncCount files are perfectly synced.`n" -ForegroundColor Green

$eventData = @{
    TargetFolder = $targetTxtPath
    Extensions   = $allowedExtensions
}

# 3. THE BACKGROUND WATCHER ACTION
$action = {
    $fullPath = $Event.SourceEventArgs.FullPath
    
    # FIX 1: Extract JUST the filename, ignoring subdirectories to prevent path errors
    $fileNameOnly = [System.IO.Path]::GetFileName($fullPath)
    $changeType = $Event.SourceEventArgs.ChangeType
    $config = $Event.MessageData

    # --- HANDLE DELETIONS ---
    if ($changeType -eq 'Deleted') {
        $deadDest = Join-Path -Path $config.TargetFolder -ChildPath "$fileNameOnly.txt"
        if (Test-Path -Path $deadDest) {
            [System.IO.File]::Delete($deadDest)
            Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] 🗑️ DELETED: Removed ghost file '$fileNameOnly.txt'." -ForegroundColor DarkRed
        }
        return 
    }

    # --- HANDLE RENAMES (Cleanup old file) ---
    if ($changeType -eq 'Renamed') {
        $oldNameOnly = [System.IO.Path]::GetFileName($Event.SourceEventArgs.OldName)
        $oldDest = Join-Path -Path $config.TargetFolder -ChildPath "$oldNameOnly.txt"
        if (Test-Path -Path $oldDest) {
            [System.IO.File]::Delete($oldDest)
            Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] 🏷️ RENAMED: Cleaned up old file '$oldNameOnly.txt'." -ForegroundColor DarkGray
        }
    }

    # --- HANDLE CREATIONS, CHANGES, AND NEW RENAMES ---
    $fileExtension = [System.IO.Path]::GetExtension($fileNameOnly).ToLower()

    if ($config.Extensions -contains $fileExtension) {
        if ($fullPath -notmatch '\\obj\\' -and $fullPath -notmatch '\\bin\\') {
            
            $newFileName = "$fileNameOnly.txt"
            $destinationPath = Join-Path -Path $config.TargetFolder -ChildPath $newFileName
            
            $maxRetries = 20 
            $success = $false
            $attempt = 0
            $lastError = ""

            Start-Sleep -Milliseconds 50 

            while (-not $success -and $attempt -lt $maxRetries) {
                try {
                    # FIX 2: Native .NET Copy (Bypasses PowerShell pipeline locks)
                    [System.IO.File]::Copy($fullPath, $destinationPath, $true)
                    $success = $true
                    
                    $time = (Get-Date).ToString('HH:mm:ss')
                    if ($changeType -eq 'Created') {
                        Write-Host "[$time] ✨ CREATED: Mirrored new file '$newFileName'." -ForegroundColor Cyan
                    } elseif ($changeType -eq 'Renamed') {
                        Write-Host "[$time] 🏷️ RENAMED: Synced under new name '$newFileName'." -ForegroundColor Cyan
                    } else {
                        Write-Host "[$time] ✅ SAVED: Updated '$fileNameOnly'." -ForegroundColor Green
                    }
                }
                catch {
                    $attempt++
                    # FIX 3: Capture the actual error reason so we aren't flying blind
                    $lastError = $_.Exception.Message
                    Start-Sleep -Milliseconds 100
                }
            }

            if (-not $success) {
                Write-Host "[$((Get-Date).ToString('HH:mm:ss'))] ❌ FAIL: '$fileNameOnly'. Reason: $lastError" -ForegroundColor Red
            }
        }
    }
}

# 4. STARTING THE ENGINE
Write-Host "Initializing FileSystemWatcher engine..." -ForegroundColor DarkGray

$events = @("EZSync_Changed", "EZSync_Created", "EZSync_Renamed", "EZSync_Deleted")
foreach ($eventName in $events) {
    Get-EventSubscriber -SourceIdentifier $eventName -ErrorAction SilentlyContinue | Unregister-Event
    Get-Job -Name $eventName -ErrorAction SilentlyContinue | Remove-Job
}

$watcher = New-Object System.IO.FileSystemWatcher
$watcher.Path = $sourceRepoPath
$watcher.Filter = "*.*"
$watcher.IncludeSubdirectories = $true

$watcher.NotifyFilter = [System.IO.NotifyFilters]::LastWrite -bor `
                        [System.IO.NotifyFilters]::FileName -bor `
                        [System.IO.NotifyFilters]::DirectoryName

$watcher.EnableRaisingEvents = $true

$jobs = @()
$jobs += Register-ObjectEvent -InputObject $watcher -EventName "Changed" -SourceIdentifier "EZSync_Changed" -MessageData $eventData -Action $action
$jobs += Register-ObjectEvent -InputObject $watcher -EventName "Created" -SourceIdentifier "EZSync_Created" -MessageData $eventData -Action $action
$jobs += Register-ObjectEvent -InputObject $watcher -EventName "Renamed" -SourceIdentifier "EZSync_Renamed" -MessageData $eventData -Action $action
$jobs += Register-ObjectEvent -InputObject $watcher -EventName "Deleted" -SourceIdentifier "EZSync_Deleted" -MessageData $eventData -Action $action

Write-Host "====================================================================" -ForegroundColor Cyan
Write-Host " >>> ULTRA-FAST LIVE SYNC ACTIVE. Syncing: $allowedExtensions <<< " -ForegroundColor Green
Write-Host "====================================================================" -ForegroundColor Cyan
Write-Host "Monitoring: $sourceRepoPath"
Write-Host "To stop:    Press [Ctrl+C]" -ForegroundColor Yellow

# 5. THE HEARTBEAT LOOP
try {
    while ($true) { Start-Sleep -Seconds 1 }
}
finally {
    Write-Host "`nShutting down monitors safely..." -ForegroundColor DarkGray
    Unregister-Event -SourceIdentifier "EZSync_Changed"
    Unregister-Event -SourceIdentifier "EZSync_Created"
    Unregister-Event -SourceIdentifier "EZSync_Renamed"
    Unregister-Event -SourceIdentifier "EZSync_Deleted"
    $watcher.Dispose()
    $jobs | Remove-Job
    Write-Host "Monitoring stopped." -ForegroundColor Green
}