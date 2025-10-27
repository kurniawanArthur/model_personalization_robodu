# 🧪 ROBO-DU Testing Checklist

## Pre-Build
- [ ] Run `./gradlew clean` untuk clean build
- [ ] Check no compile errors
- [ ] Verify all XML layouts valid

## Installation
- [ ] Build APK: `./gradlew assembleDebug`
- [ ] Install to device/emulator
- [ ] App launches without crash

---

## ✅ Feature 1: Custom Class Names

### Test 1.1: Edit Single Class
- [ ] Launch app
- [ ] Long-press pada "Class 1" button
- [ ] Dialog "Edit Class Name" muncul
- [ ] Ketik "Cat" di EditText
- [ ] Press "Save"
- [ ] **Expected**: Button label berubah jadi "Cat"
- [ ] Toast muncul: "Class name updated to 'Cat'"

### Test 1.2: Edit All Classes
- [ ] Long-press "Class 2" → Rename to "Dog"
- [ ] Long-press "Class 3" → Rename to "Bird"
- [ ] Long-press "Class 4" → Rename to "Fish"
- [ ] **Expected**: All labels updated

### Test 1.3: Persistence Test
- [ ] Force close app (swipe from recent apps)
- [ ] Relaunch app
- [ ] **Expected**: Class names still "Cat", "Dog", "Bird", "Fish"
- [ ] ✅ PASS if names persist

### Test 1.4: Empty Name Validation
- [ ] Long-press any class
- [ ] Delete all text (empty)
- [ ] Press "Save"
- [ ] **Expected**: Toast "Class name cannot be empty"
- [ ] Label tidak berubah

---

## ✅ Feature 2: Model Manager

### Test 2.1: Access Model Manager
- [ ] Look at toolbar
- [ ] **Expected**: See 4 icons: [?] [💾] [⚙️]
- [ ] Orange save icon (💾) visible
- [ ] Tap save icon
- [ ] **Expected**: Dialog "Saved Models" opens

### Test 2.2: Empty State
- [ ] (If no models saved yet)
- [ ] **Expected**: Message "No saved models yet..."
- [ ] RecyclerView tidak tampil
- [ ] "Clear All" button ada
- [ ] "Close" button ada

### Test 2.3: Save First Model
- [ ] Close Model Manager dialog
- [ ] Capture 5+ samples for "Cat"
- [ ] Capture 5+ samples for "Dog"
- [ ] Press "Train Model"
- [ ] Wait for training complete (watch progress bar)
- [ ] Open Model Manager again
- [ ] Look for save functionality (might need to implement save button in camera fragment)

### Test 2.4: Model List Display
- [ ] (After models saved)
- [ ] Open Model Manager
- [ ] **Expected**: RecyclerView dengan list models
- [ ] Each card shows:
  - [ ] Model name dengan accuracy & date
  - [ ] Class names (e.g., "Cat, Dog, Bird, Fish")
  - [ ] Sample count & file size
  - [ ] Upload icon (load button)
  - [ ] Trash icon (delete button)
- [ ] Storage info shows "Storage: X KB"

### Test 2.5: Delete Single Model
- [ ] Tap trash icon on any model
- [ ] **Expected**: Confirmation dialog
- [ ] Message: "Are you sure you want to delete..."
- [ ] Tap "Delete"
- [ ] **Expected**: Toast "Model deleted"
- [ ] Model removed from list
- [ ] Storage info updated

### Test 2.6: Clear All Models
- [ ] (With multiple models saved)
- [ ] Tap "Clear All" button
- [ ] **Expected**: Confirmation dialog
- [ ] Message: "...delete ALL saved models? This cannot be undone"
- [ ] Tap "Delete All"
- [ ] **Expected**: Toast "All models cleared"
- [ ] Empty state message appears
- [ ] Storage: 0 KB

---

## ✅ Feature 3: Training Progress

### Test 3.1: Progress Bar Visibility
- [ ] Capture samples for 2+ classes
- [ ] Press "Train Model"
- [ ] **Expected**: ProgressBar appears
- [ ] Bar color: Orange (ROBO-DU theme)
- [ ] Initial value: 0%

### Test 3.2: Progress Updates
- [ ] During training, watch progress bar
- [ ] **Expected**: Bar animates smoothly
- [ ] Progress increases: 0% → 10% → 20% → ... → 100%
- [ ] Updates happen regularly (not stuck)

### Test 3.3: Epoch Counter
- [ ] Look below progress bar
- [ ] **Expected**: Text "Epoch: X"
- [ ] Counter updates: 1, 2, 3, 4...
- [ ] Matches progress bar position

### Test 3.4: Training Complete
- [ ] Wait for training to finish
- [ ] **Expected**: 
  - [ ] Toast "Training complete! Accuracy: X%"
  - [ ] Progress bar reaches 100%
  - [ ] Epoch counter shows final count
  - [ ] State changes to PAUSE

---

## ✅ Feature 4: Training Validation

### Test 4.1: No Samples
- [ ] Fresh install (no samples captured)
- [ ] Press "Train Model"
- [ ] **Expected**: Error "No training samples available!"
- [ ] Training tidak start

### Test 4.2: Too Few Total Samples
- [ ] Capture 2 samples Class 1
- [ ] Capture 2 samples Class 2
- [ ] Press "Train Model" (total: 4 samples)
- [ ] **Expected**: Error "Too few samples! Need at least 5 total"
- [ ] Training tidak start

### Test 4.3: Single Class Only
- [ ] Capture 10 samples Class 1 only
- [ ] Press "Train Model"
- [ ] **Expected**: Error "Need at least 2 different classes"
- [ ] Training tidak start

### Test 4.4: Insufficient Per Class
- [ ] Capture 5 samples Class 1
- [ ] Capture 2 samples Class 2 (< 3)
- [ ] Press "Train Model"
- [ ] **Expected**: Error "Some classes have too few samples..."
- [ ] Training tidak start

### Test 4.5: Valid Samples
- [ ] Capture 5 samples Class 1
- [ ] Capture 5 samples Class 2
- [ ] Press "Train Model"
- [ ] **Expected**: Training starts successfully
- [ ] Progress bar animates
- [ ] No errors

---

## 🎨 Visual/Branding Tests

### Test 5.1: ROBO-DU Branding
- [ ] Launch app
- [ ] Toolbar shows "ROBO-DU" (not TensorFlow logo)
- [ ] Text color: Orange (#FF6F00)
- [ ] Font: Bold, sans-serif-black
- [ ] Subtitle: "Model Personalization"
- [ ] Badge: "Enhanced"

### Test 5.2: Color Consistency
- [ ] Progress bar: Orange
- [ ] Model Manager icon: Orange
- [ ] Dialog titles: Orange
- [ ] Buttons: Appropriate colors (green/red/gray)

---

## 🔄 Integration Tests

### Test 6.1: Full Workflow
- [ ] Launch app
- [ ] Edit all 4 class names
- [ ] Capture 5+ samples each class
- [ ] Train model (watch progress)
- [ ] Training completes
- [ ] Open Model Manager
- [ ] Verify model appears in list
- [ ] Restart app
- [ ] Verify class names persist
- [ ] Open Model Manager
- [ ] Verify model still in list

### Test 6.2: Multiple Training Sessions
- [ ] Train first model
- [ ] Save to Model Manager
- [ ] Change class names
- [ ] Capture new samples
- [ ] Train second model
- [ ] Save to Model Manager
- [ ] Open Model Manager
- [ ] **Expected**: Both models visible
- [ ] Different names/accuracy/timestamps

---

## 🐛 Edge Cases

### Test 7.1: App Rotation
- [ ] During training
- [ ] Rotate device
- [ ] **Expected**: Progress persists
- [ ] Training continues

### Test 7.2: Background/Foreground
- [ ] Start training
- [ ] Press Home button
- [ ] Wait 10 seconds
- [ ] Return to app
- [ ] **Expected**: Training continues or resumes

### Test 7.3: Very Long Class Name
- [ ] Long-press class
- [ ] Enter 50+ character name
- [ ] **Expected**: Name truncated or handled gracefully

### Test 7.4: Special Characters
- [ ] Try name: "Cat/Dog #1 (test)"
- [ ] **Expected**: Name saved (special chars handled)

---

## 📊 Test Results Template

```
Date: ___________
Device: ___________
Android Version: ___________

Feature 1 - Custom Class Names:    ✅ PASS / ❌ FAIL
Feature 2 - Model Manager:         ✅ PASS / ❌ FAIL
Feature 3 - Training Progress:     ✅ PASS / ❌ FAIL
Feature 4 - Training Validation:   ✅ PASS / ❌ FAIL
Feature 5 - Branding:              ✅ PASS / ❌ FAIL
Feature 6 - Integration:           ✅ PASS / ❌ FAIL
Feature 7 - Edge Cases:            ✅ PASS / ❌ FAIL

Overall Status: ✅ READY / ⚠️ NEEDS FIX / ❌ BROKEN

Notes:
_________________________________
_________________________________
_________________________________
```

---

## 🚨 Known Issues / TODO

### Known Limitations:
1. ⚠️ **Load Model functionality**: Button exists but needs wiring to TransferLearningHelper
   - Currently shows toast but doesn't actually load model weights
   - Need to implement callback from MainActivity → CameraFragment

2. ⚠️ **Save Model button**: Need to add explicit "Save Model" button in CameraFragment
   - Currently ModelManager can list models but no UI to trigger save
   - Recommend adding button near "Train Model" button

### Future Improvements:
1. Add "Save Model" button in camera UI
2. Wire up "Load Model" to actually restore weights
3. Show loading indicator during model load
4. Add model accuracy to card display
5. Implement sample gallery backend

---

## 📝 Bug Report Template

If you find issues:

```markdown
### Bug Description
What happened?

### Expected Behavior
What should happen?

### Steps to Reproduce
1. 
2. 
3. 

### Device Info
- Device: 
- Android Version:
- App Version:

### Logs/Screenshots
(Attach logcat or screenshots)
```

---

## ✅ Sign-Off

After testing, fill this:

```
Tested By: _______________
Date: _______________
Signature: _______________

✅ All critical features working
✅ No crashes or ANRs
✅ UI responsive and smooth
✅ Data persists correctly

Ready for: 
[ ] Production
[ ] Beta Testing
[ ] Needs Fixes (see notes)
```

---

**Happy Testing! 🧪**
