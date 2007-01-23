package com.intellij.localvcs.integration;

import com.intellij.localvcs.Entry;
import com.intellij.localvcs.ILocalVcs;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Updater {
  // todo algorithm could be simplified and improved
  // todo refactor and integrate with CacheUpdater

  private ILocalVcs myVcs;
  private LocalFileSystem myFileSystem;
  private FileFilter myFilter;
  private VirtualFile[] myRoots;

  public static void update(ILocalVcs vcs, LocalFileSystem fs, FileFilter filter, VirtualFile... roots) throws IOException {
    new Updater(vcs, fs, filter, roots).update();
  }

  private Updater(ILocalVcs vcs, LocalFileSystem fs, FileFilter filter, VirtualFile... roots) {
    myVcs = vcs;
    myFileSystem = fs;
    myFilter = filter;
    myRoots = selectNonNestedRoots(roots);
  }

  private VirtualFile[] selectNonNestedRoots(VirtualFile... roots) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (VirtualFile left : roots) {
      if (!isNested(left, roots)) result.add(left);
    }
    return result.toArray(new VirtualFile[0]);
  }

  private boolean isNested(VirtualFile f, VirtualFile... roots) {
    for (VirtualFile another : roots) {
      if (f == another) continue;
      if (VfsUtil.isAncestor(another, f, false)) return true;
    }
    return false;
  }

  public void update() throws IOException {
    // todo maybe we should delete before updating for optimization
    createNewRoots();
    updateRoots();
    deleteRemovedRoots();

    myVcs.apply();
  }

  private void createNewRoots() throws IOException {
    for (VirtualFile f : myRoots) {
      if (!myVcs.hasEntry(f.getPath())) {
        myVcs.createDirectory(f.getPath(), f.getTimeStamp());
      }
    }
  }

  private void updateRoots() throws IOException {
    for (VirtualFile f : myRoots) {
      updateDirectory(f);
    }
  }

  private void deleteRemovedRoots() {
    for (Entry r : myVcs.getRoots()) {
      if (!hasRoot(r)) myVcs.delete(r.getPath());
    }
  }

  private boolean hasRoot(Entry r) {
    for (VirtualFile f : myRoots) {
      if (f.getPath().equals(r.getPath())) return true;
    }
    return false;
  }

  private void updateDirectory(VirtualFile dir) throws IOException {
    Entry e = myVcs.findEntry(dir.getPath());
    if (e != null) deleteAbsentEntries(e, dir);

    createNewEntries(dir);
    updateOutdatedEntries(dir);
  }

  private void deleteAbsentEntries(Entry entry, VirtualFile dir) {
    for (Entry e : entry.getChildren()) {
      VirtualFile f = dir.findChild(e.getName());
      if (!areOfTheSameKind(e, f)) {
        myVcs.delete(e.getPath());
      }
      else {
        if (e.isDirectory()) {
          deleteAbsentEntries(e, f);
        }
      }
    }
  }

  private void createNewEntries(VirtualFile dir) throws IOException {
    for (VirtualFile f : dir.getChildren()) {
      if (!myFilter.isAllowed(f)) continue;

      Entry e = myVcs.findEntry(f.getPath());
      if (!areOfTheSameKind(e, f)) {
        if (f.isDirectory()) {
          myVcs.createDirectory(f.getPath(), f.getTimeStamp());
          createNewEntries(f);
        }
        else {
          myVcs.createFile(f.getPath(), physicalContentOf(f), f.getTimeStamp());
        }
      }
    }
  }

  private void updateOutdatedEntries(VirtualFile dir) throws IOException {
    for (VirtualFile f : dir.getChildren()) {
      Entry e = myVcs.findEntry(f.getPath());
      if (areOfTheSameKind(e, f)) {
        if (f.isDirectory()) {
          updateOutdatedEntries(f);
        }
        else if (e.isOutdated(f.getTimeStamp())) {
          myVcs.changeFileContent(f.getPath(), physicalContentOf(f), f.getTimeStamp());
        }
      }
    }
  }

  private byte[] physicalContentOf(VirtualFile f) throws IOException {
    return myFileSystem.physicalContentsToByteArray(f);
  }

  private boolean areOfTheSameKind(Entry e, VirtualFile f) {
    if (e == null || f == null) return false;
    return e.isDirectory() == f.isDirectory();
  }
}
