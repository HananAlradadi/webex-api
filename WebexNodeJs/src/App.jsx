import { useState } from 'react';

function App() {
  const [meetingId, setMeetingId] = useState('');
  const [loading, setLoading] = useState(false);

  const joinMeeting = async () => {
    if (!meetingId) {
      alert('Please enter a meeting ID');
      return;
    }

    setLoading(true);

    try {
      const res = await fetch('http://localhost:3000/webex/join-link', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ meetingNumber: meetingId })
      });

      const text = await res.text();
      let data;
      try {
        data = JSON.parse(text);
      } catch (e) {
        console.error("Response is not valid JSON:", text);
        alert("Server returned invalid response");
        return;
      }

      if (res.ok && data.joinLink) {
        window.location.href = data.joinLink;
      } else {
        alert('Failed to get join link');
        console.error(data);
      }
    } catch (err) {
      console.error('Error joining meeting:', err);
      alert('Error joining meeting');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ padding: '20px', fontFamily: 'Arial' }}>
      <h2>Join Webex Meeting</h2>
      <input
        type="text"
        value={meetingId}
        onChange={(e) => setMeetingId(e.target.value)}
        placeholder="Enter Meeting Number"
        style={{ width: '300px', padding: '8px' }}
      />
      <br /><br />
      <button onClick={joinMeeting} style={{ padding: '10px 20px' }} disabled={loading}>
        {loading ? 'Joining...' : 'Join'}
      </button>
    </div>
  );
}

export default App;
