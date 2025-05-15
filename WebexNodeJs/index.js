// Webex Audio Stream Bot using @webex/sdk + FFmpeg + Spring Boot Integration

import Webex from 'webex';

import { exec } from 'child_process';
import axios from 'axios';

const SPRING_BOOT_BASE_URL = 'http://localhost:8080/webex';
const displayName = "Hanan صوتك";

let ffmpegProcess = null;

function startFFmpeg() {
  console.log('▶️ Starting FFmpeg...');
  ffmpegProcess = exec(`
    ffmpeg -f avfoundation -i ":BlackHole 2ch" -ac 1 -ar 16000 -f wav - |
    curl -X POST ${SPRING_BOOT_BASE_URL}/audio-stream \
    -H "Content-Type: audio/wav" \
    --data-binary @-
  `, (error) => {
    if (error) {
      console.error(`❌ FFmpeg error: ${error.message}`);
    }
  });
}

function stopFFmpeg(reason) {
  if (ffmpegProcess) {
    ffmpegProcess.kill('SIGINT');
    console.log(`🛑 FFmpeg stopped: ${reason}`);
    ffmpegProcess = null;
  }
}

async function fetchWebexData() {
  const tokenResponse = await axios.get(`${SPRING_BOOT_BASE_URL}/token`);
  const meetingResponse = await axios.post(`${SPRING_BOOT_BASE_URL}/create-meeting`);
  const webLink = meetingResponse.data.webLink;

  const encodedName = encodeURIComponent(displayName);
  const fullJoinLink = `${webLink}&AN=${encodedName}`;

  return {
    accessToken: tokenResponse.data.access_token,
    meetingLink: fullJoinLink
  };
}

async function joinMeeting() {
  try {
    const { accessToken, meetingLink } = await fetchWebexData();

    const webex = Webex.init({
      credentials: {
        access_token: accessToken
      }
    });

    const meeting = await webex.meetings.create(meetingLink);
    await meeting.join();

    console.log(`✅ Joined Webex as "${displayName}"`);
    startFFmpeg();

    meeting.on('meeting:ended', () => stopFFmpeg('Meeting ended'));
    meeting.on('meeting:left', () => stopFFmpeg('User left the meeting'));
    meeting.on('error', (err) => {
      console.error('❌ Meeting error:', err);
      stopFFmpeg('Error occurred');
    });

    meeting.on('media:stopped', (media) => {
      console.log('⚠️ Media stopped:', media.type);
      stopFFmpeg('Media stopped');
    });

    meeting.on('media:ready', (media) => {
      console.log('✅ Media reconnected:', media.type);
      if (!ffmpegProcess) {
        startFFmpeg();
      }
    });

    meeting.on('members:update', (payload) => {
      const self = payload.self;
      if (self?.state === 'IN_MEETING' && !ffmpegProcess) {
        console.log('✅ User in meeting');
        startFFmpeg();
      } else if (self?.state === 'NOT_IN_MEETING') {
        console.log('❌ User not in meeting');
        stopFFmpeg('User left');
      }
    });

  } catch (err) {
    console.error('❌ Failed to join meeting:', err);
  }
}

joinMeeting();
