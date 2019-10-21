package test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class WriterTest {
	public static void main(String[] args) {
		List<Integer> list = new ArrayList<>();
		list.add(Integer.valueOf(1));
		list.add(Integer.valueOf(2));
		list.add(Integer.valueOf(3));
		list.add(Integer.valueOf(4));
		list.add(Integer.valueOf(5));
		list.add(Integer.valueOf(6));
		int delay = 0;
		for (Iterator<Integer> i = list.iterator(); i.hasNext();) {
			if (delay < 3) {
				delay++;
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				System.out.println(i.next());
				delay = 0;
			}
		}
	}
}