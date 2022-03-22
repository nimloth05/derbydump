package au.com.ish.derbydump.derbydump.util;

import java.util.List;
import java.util.ListIterator;
import java.util.function.Predicate;

public final class ListUtil {

  public static <T> int indexOf(List<T> list, Predicate<? super T> predicate) {
    for(ListIterator<T> iter = list.listIterator(); iter.hasNext(); )
      if(predicate.test(iter.next())) return iter.previousIndex();
    return -1;
  }}
