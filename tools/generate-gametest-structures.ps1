<#
.SYNOPSIS
    Writes the empty structure templates that this mod's GameTests are named after.

.DESCRIPTION
    Vanilla derives a GameTest's structure id from its declaring class and method name -
    tieredechoes:<ClassName>.<methodName>, both lowercased - and loads it from
    data/tieredechoes/structures/<ClassName>.<methodName>.nbt. A missing file crashes the
    game test server before a single test runs.

    Every test here asserts on code rather than on blocks, so one identical all-air box is
    enough for all of them; only the file name differs. The script derives those names by
    scanning the sources, which keeps them from drifting when a test is renamed.

    NBT is written by hand rather than through Minecraft so the build never needs a running
    game. The layout must match StructureTemplate#load:
      size     -> TAG_List of three TAG_Int   (not an int array)
      palette  -> TAG_List of one compound { Name: "minecraft:air" }
      blocks   -> TAG_List of TAG_Compound, empty: nothing to place
      entities -> TAG_List of TAG_Compound, empty

    This script is deliberately ASCII-only: PowerShell 5.1 reads BOM-less scripts with the
    system codepage, so a stray typographic character here becomes a parse error.

.EXAMPLE
    powershell -File tools/generate-gametest-structures.ps1
#>
[CmdletBinding()]
param(
    [string] $SourceDir = (Join-Path $PSScriptRoot '..\src\main\java'),

    [string] $OutputDir = (Join-Path $PSScriptRoot '..\src\main\resources\data\tieredechoes\structures'),

    [int] $Size = 5,

    # 1.20.1 - see SharedConstants.WORLD_VERSION.
    [int] $DataVersion = 3465
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Write-UInt16([System.IO.Stream] $Stream, [uint16] $Value) {
    $bytes = [BitConverter]::GetBytes($Value)
    if ([BitConverter]::IsLittleEndian) { [Array]::Reverse($bytes) }
    $Stream.Write($bytes, 0, $bytes.Length)
}

function Write-Int32([System.IO.Stream] $Stream, [int] $Value) {
    $bytes = [BitConverter]::GetBytes($Value)
    if ([BitConverter]::IsLittleEndian) { [Array]::Reverse($bytes) }
    $Stream.Write($bytes, 0, $bytes.Length)
}

function Write-NbtString([System.IO.Stream] $Stream, [string] $Value) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
    Write-UInt16 $Stream ([uint16] $bytes.Length)
    $Stream.Write($bytes, 0, $bytes.Length)
}

function Write-NbtByte([System.IO.Stream] $Stream, [int] $Value) {
    $Stream.WriteByte([byte] $Value)
}

function New-StructureBytes([int] $Size, [int] $DataVersion) {
    $buffer = New-Object System.IO.MemoryStream
    try {
        Write-NbtByte $buffer 0x0A         # root TAG_Compound
        Write-UInt16 $buffer 0              # unnamed root, as vanilla writes it

        # size is a TAG_List whose element type is TAG_Int (0x03), not an int array.
        Write-NbtByte $buffer 0x09
        Write-NbtString $buffer 'size'
        Write-NbtByte $buffer 0x03
        Write-Int32 $buffer 3
        foreach ($axis in 1..3) { Write-Int32 $buffer $Size }

        Write-NbtByte $buffer 0x09
        Write-NbtString $buffer 'entities'
        Write-NbtByte $buffer 0x0A
        Write-Int32 $buffer 0

        Write-NbtByte $buffer 0x09
        Write-NbtString $buffer 'blocks'
        Write-NbtByte $buffer 0x0A
        Write-Int32 $buffer 0

        Write-NbtByte $buffer 0x09
        Write-NbtString $buffer 'palette'
        Write-NbtByte $buffer 0x0A
        Write-Int32 $buffer 1
        Write-NbtByte $buffer 0x08          # TAG_String
        Write-NbtString $buffer 'Name'
        Write-NbtString $buffer 'minecraft:air'
        Write-NbtByte $buffer 0x00          # end of the palette entry

        # 0x03 is TAG_Int. A mistyped tag id here desynchronises the reader for every
        # entry after it, and the failure surfaces as an unrelated EOFException.
        Write-NbtByte $buffer 0x03
        Write-NbtString $buffer 'DataVersion'
        Write-Int32 $buffer $DataVersion

        Write-NbtByte $buffer 0x00          # end of root
        return $buffer.ToArray()
    }
    finally {
        $buffer.Dispose()
    }
}

# Mirrors GameTestRegistry: <simple class name>.<method name>, both lowercased.
function Get-StructureIds([string] $SourceDir) {
    $ids = @()
    $files = Get-ChildItem -Recurse $SourceDir -Filter '*.java' |
        Where-Object { $_.Directory.Name -eq 'gametest' -and $_.FullName -like '*\common\gametest\*' }

    foreach ($file in $files) {
        $text = Get-Content $file.FullName -Raw
        if ($text -notmatch '@GameTestHolder') { continue }
        if ($text -notmatch '(?m)^\s*public\s+(?:final\s+)?class\s+(\w+)') { continue }
        $className = $Matches[1]

        $methodPattern = '@GameTest(?:\s*\([^)]*\))?\s+(?:public\s+)?static\s+void\s+(\w+)'
        foreach ($match in [regex]::Matches($text, $methodPattern)) {
            $ids += "$($className.ToLowerInvariant()).$($match.Groups[1].Value.ToLowerInvariant())"
        }
    }
    return $ids | Sort-Object -Unique
}

$ids = @(Get-StructureIds -SourceDir (Resolve-Path $SourceDir).Path)
if ($ids.Count -eq 0) {
    throw "No @GameTest methods found under $SourceDir - nothing to generate."
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$bytes = New-StructureBytes -Size $Size -DataVersion $DataVersion

foreach ($id in $ids) {
    $path = Join-Path $OutputDir "$id.nbt"
    if (Test-Path $path) {
        Write-Host "unchanged  $id.nbt"
        continue
    }
    $file = [System.IO.File]::Create($path)
    try {
        $gzip = New-Object System.IO.Compression.GZipStream($file, [System.IO.Compression.CompressionMode]::Compress)
        try {
            $gzip.Write($bytes, 0, $bytes.Length)
        }
        finally {
            $gzip.Dispose()
        }
    }
    finally {
        $file.Dispose()
    }
    Write-Host "generated  $id.nbt"
}

Write-Host "structure templates: $($ids.Count)"
