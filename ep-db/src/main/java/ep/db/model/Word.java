package ep.db.model;

import java.util.Comparator;

public class Word implements Comparable<Word>{

	private String text;
	
	private int size;
	
	public static final WordComparator wordComparator = new WordComparator();
	
	public Word(String text, int size) {
		this.text = text;
		this.size = size;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}
	
	@Override
	public int compareTo(Word o) {
		return wordComparator.compare(this,o);
	}		
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((text == null) ? 0 : text.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Word other = (Word) obj;
		if (text == null) {
			if (other.text != null)
				return false;
		} else if (!text.equals(other.text))
			return false;
		return true;
	}



	public static class WordComparator implements Comparator<Word> {
		@Override
		public int compare(Word w1, Word w2) {
			return Integer.compare(w1.size, w2.size);
		}
		
	}

	
}
