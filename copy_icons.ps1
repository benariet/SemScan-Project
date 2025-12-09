# Copy QR code icon files from Downloads to mipmap folders
$source = "c:\Users\benariet\Downloads\ImageSets\android"
$dest = "src\main\res"

Write-Host "Copying icon files..."

Copy-Item "$source\drawable-mdpi\Gemini_Generated_Image_wisxhjwisxhjwisx.png" "$dest\mipmap-mdpi\ic_launcher_foreground.png" -Force
Copy-Item "$source\drawable-hdpi\Gemini_Generated_Image_wisxhjwisxhjwisx.png" "$dest\mipmap-hdpi\ic_launcher_foreground.png" -Force
Copy-Item "$source\drawable-xhdpi\Gemini_Generated_Image_wisxhjwisxhjwisx.png" "$dest\mipmap-xhdpi\ic_launcher_foreground.png" -Force
Copy-Item "$source\drawable-xxhdpi\Gemini_Generated_Image_wisxhjwisxhjwisx.png" "$dest\mipmap-xxhdpi\ic_launcher_foreground.png" -Force
Copy-Item "$source\drawable-xxxhdpi\Gemini_Generated_Image_wisxhjwisxhjwisx.png" "$dest\mipmap-xxxhdpi\ic_launcher_foreground.png" -Force

Write-Host "Removing XML placeholder files..."
Remove-Item "$dest\mipmap-mdpi\ic_launcher_foreground.xml" -ErrorAction SilentlyContinue
Remove-Item "$dest\mipmap-hdpi\ic_launcher_foreground.xml" -ErrorAction SilentlyContinue
Remove-Item "$dest\mipmap-xhdpi\ic_launcher_foreground.xml" -ErrorAction SilentlyContinue
Remove-Item "$dest\mipmap-xxhdpi\ic_launcher_foreground.xml" -ErrorAction SilentlyContinue
Remove-Item "$dest\mipmap-xxxhdpi\ic_launcher_foreground.xml" -ErrorAction SilentlyContinue

Write-Host "Done! Icon files copied successfully."

