export const CURRENT_CONFIG = {

  // license
  appId: '157899', // You need to go to the development website to apply.
  appKey: '342af765a8517178f33bb9b233cce63', // You need to go to the development website to apply.
  appLicense: 'OUVypHn7s10STrX+2R+eJJd4PyFh0roAkJWNZVXQFOnZUpVWunFhfH/FdUu+HMsMXuIZ7U8lW3S0wNNj/Si1oDdAVOAlpsDk1rNM1/ccUunRkxrjqEcBeoDKiuxIypghDS7P/AUzUemP5Y0EopdYfkWAP5ZMa9SbbXK9/09qub4=', // You need to go to the development website to apply.

  // http
  baseURL: 'http://127.0.0.1:6789/', // This url must end with "/". Example: 'http://192.168.1.1:6789/'
  websocketURL: 'ws://127.0.0.1:6789/api/v1/ws', // Example: 'ws://192.168.1.1:6789/api/v1/ws'

  // livestreaming
  // RTMP  Note: This IP is the address of the strdeaming server. If you want to see livestream on web page, you need to convert the RTMP stream to WebRTC stream.
  rtmpURL: 'rtmp://47.97.196.173:1935/live/', // Example: 'rtmp://192.168.1.1/live/'
  // GB28181 Note:If you don't know what these parameters mean, you can go to Pilot2 and select the GB28181 page in the cloud platform. Where the parameters same as these parameters.
  gbServerIp: 'Please enter the server ip.',
  gbServerPort: 'Please enter the server port.',
  gbServerId: 'Please enter the server id.',
  gbAgentId: 'Please enter the agent id',
  gbPassword: 'Please enter the agent password',
  gbAgentPort: 'Please enter the local port.',
  gbAgentChannel: 'Please enter the channel.',
  // RTSP
  rtspUserName: 'root',
  rtspPassword: 'root',
  rtspPort: '8554',
  // Agora
  agoraAPPID: 'Please enter the agora app id.',
  agoraToken: 'Please enter the agora temporary token.',
  agoraChannel: 'Please enter the agora channel.',

  // map
  // You can apply on the AMap website.
  amapKey: '7467c2d6ea1e0d777383bb71d0b2c8f1'

}
