Add-Type -AssemblyName System.IO.Compression.FileSystem

$hpiPath = "D:\GIT\swarm-agents-cloud\target\swarm-agents-cloud.hpi"
$jellySource = "D:\GIT\swarm-agents-cloud\target\classes\io\jenkins\plugins\swarmcloud\SwarmDashboard\index.jelly"
$jellyEntry = "io/jenkins/plugins/swarmcloud/SwarmDashboard/index.jelly"

Write-Host "Opening HPI archive: $hpiPath"
$zip = [IO.Compression.ZipFile]::Open($hpiPath, 'Update')

try {
    # Remove old entry if exists
    $entry = $zip.GetEntry($jellyEntry)
    if ($entry) {
        Write-Host "Removing old entry: $jellyEntry"
        $entry.Delete()
    }

    # Add updated file
    Write-Host "Adding updated file: $jellyEntry"
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $jellySource, $jellyEntry, 'Optimal')

    Write-Host "Successfully updated index.jelly in HPI"
} finally {
    $zip.Dispose()
}

Write-Host "Done!"
